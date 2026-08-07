package com.clearfolio.viewer.lifecycle;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Coordinates receipt-first metadata tombstoning and restart-safe artifact cleanup.
 *
 * <p>Each document lifecycle is serialized by a fixed-memory per-job lock shared
 * with conversion. An authorized request is persisted before the first artifact
 * read. If that read is temporarily unavailable, metadata remains intact and the
 * failed snapshot attempt is durably recorded so later work remains eligible
 * after restart. Metadata tombstoning begins only after the exact artifact digest
 * (or explicit absence digest) is durable.</p>
 *
 * <p>Different document identifiers may progress concurrently, so a slow
 * artifact store cannot serialize every deletion and recovery task in the
 * process. Replaceable durable adapters may use transactions, object-generation
 * preconditions, compare-and-set state, or a single-consumer outbox while
 * preserving the same per-generation contract.</p>
 */
@Component
public class ArtifactDeletionCoordinator {

    static final String ABSENT_ARTIFACT_CHECKSUM =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String FAILURE_CHECKSUM_MISMATCH = "artifact_checksum_mismatch";
    private static final String FAILURE_DELETE = "artifact_store_delete_failed";
    private static final String FAILURE_READ = "artifact_store_read_failed";
    private static final Logger log = LoggerFactory.getLogger(ArtifactDeletionCoordinator.class);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final ConversionJobRepository repository;
    private final ArtifactStore artifactStore;
    private final ArtifactDeletionReceiptStore receiptStore;
    private final ArtifactDeletionMetrics metrics;
    private final ArtifactLifecycleLockRegistry lifecycleLocks;
    private final int maxReceiptsPerRun;

    /**
     * Creates the Spring-managed coordinator and validates the recovery bound.
     *
     * @param repository conversion-job metadata repository
     * @param artifactStore document artifact store
     * @param receiptStore durable deletion receipt store
     * @param metrics low-cardinality cleanup evidence
     * @param lifecycleLocks per-job conversion/deletion serialization boundary
     * @param maxReceiptsPerRun maximum incomplete receipts processed in one pass
     * @throws NullPointerException when a required collaborator is absent
     * @throws IllegalArgumentException when the recovery batch bound is not positive
     */
    @Autowired
    public ArtifactDeletionCoordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics,
            ArtifactLifecycleLockRegistry lifecycleLocks,
            @Value("${clearfolio.artifact-deletion-cleanup.max-receipts-per-run:100}")
            int maxReceiptsPerRun
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.receiptStore = Objects.requireNonNull(receiptStore, "receiptStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.lifecycleLocks = Objects.requireNonNull(lifecycleLocks, "lifecycleLocks");
        if (maxReceiptsPerRun <= 0) {
            throw new IllegalArgumentException("maxReceiptsPerRun must be positive");
        }
        this.maxReceiptsPerRun = maxReceiptsPerRun;
    }

    /**
     * Creates a standalone coordinator using the process-wide lifecycle locks.
     *
     * @param repository conversion-job metadata repository
     * @param artifactStore document artifact store
     * @param receiptStore durable deletion receipt store
     * @param metrics low-cardinality cleanup evidence
     * @param maxReceiptsPerRun maximum incomplete receipts processed in one pass
     */
    public ArtifactDeletionCoordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics,
            int maxReceiptsPerRun
    ) {
        this(
                repository,
                artifactStore,
                receiptStore,
                metrics,
                ArtifactLifecycleLockRegistry.shared(),
                maxReceiptsPerRun
        );
    }

    /**
     * Deletes one tenant-owned job and starts durable artifact cleanup.
     *
     * <p>Artifact-store failure is retained as a retryable receipt. A repeated
     * request by the same tenant resumes or observes the existing receipt and
     * returns the same intended result without recreating work.</p>
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param tenantId authenticated tenant identifier
     * @return true when an owned job entered or already belongs to the durable
     *         deletion lifecycle; false for missing or cross-tenant identifiers
     * @throws NullPointerException when either identifier is absent
     */
    public boolean deleteForTenant(UUID jobId, String tenantId) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        String requiredTenantId = Objects.requireNonNull(tenantId, "tenantId").strip();
        return lifecycleLocks.withJobLock(
                requiredJobId,
                () -> deleteForTenantLocked(requiredJobId, requiredTenantId)
        );
    }

    /**
     * Preserves the legacy unscoped deletion entry point using durable receipts
     * whenever metadata identifies the owning lifecycle.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @throws NullPointerException when the identifier is absent
     */
    public void deleteGlobally(UUID jobId) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        lifecycleLocks.withJobLock(requiredJobId, () -> {
            deleteGloballyLocked(requiredJobId);
            return null;
        });
    }

    /**
     * Replays one bounded deterministic batch of incomplete deletion receipts.
     *
     * <p>A failure in one receipt is isolated so later receipts in the same
     * batch remain eligible. The receipt store orders incomplete work by its
     * durable last transition or failed-attempt time, so a failed item moves
     * behind older eligible work without relying on process-local cursor state.
     * The same order is reconstructed after restart.</p>
     *
     * @return number of receipts selected for this recovery pass
     */
    public synchronized int retryPendingWork() {
        List<ArtifactDeletionReceipt> pending = receiptStore.pendingReceipts();
        int selected = Math.min(maxReceiptsPerRun, pending.size());
        for (int index = 0; index < selected; index++) {
            try {
                resumeReceipt(pending.get(index));
            } catch (RuntimeException exception) {
                metrics.recordFailed();
                log.warn(
                        "Artifact deletion recovery retained an incomplete receipt. cause={}",
                        exception.getClass().getName()
                );
            }
        }
        return selected;
    }

    /** Replays incomplete receipts when the application becomes ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingAfterStartup() {
        retryPendingWork();
    }

    /** Replays a bounded receipt batch after the configured fixed delay. */
    @Scheduled(fixedDelayString = "${clearfolio.artifact-deletion-cleanup.retry-delay-ms:30000}")
    public void retryPendingAfterDelay() {
        retryPendingWork();
    }

    void resumeReceipt(ArtifactDeletionReceipt candidate) {
        ArtifactDeletionReceipt requiredCandidate = Objects.requireNonNull(candidate, "candidate");
        lifecycleLocks.withJobLock(requiredCandidate.jobId(), () -> {
            resumeReceiptLocked(requiredCandidate);
            return null;
        });
    }

    private boolean deleteForTenantLocked(UUID jobId, String tenantId) {
        Optional<ConversionJob> existing = repository.findByTenantAndId(tenantId, jobId);
        if (existing.isEmpty()) {
            return resumeExistingReceiptForTenantLocked(jobId, tenantId);
        }
        ensureSha256Available();
        ArtifactDeletionReceipt receipt = requestReceipt(tenantId, jobId);
        resumeReceiptLocked(receipt);
        return true;
    }

    private void deleteGloballyLocked(UUID jobId) {
        Optional<ArtifactDeletionReceipt> existingReceipt = receiptStore.findByJobId(jobId);
        if (existingReceipt.isPresent()) {
            resumeReceiptLocked(existingReceipt.get());
            return;
        }
        Optional<ConversionJob> existing = repository.findById(jobId);
        if (existing.isEmpty()) {
            repository.deleteById(jobId);
            return;
        }
        ensureSha256Available();
        ConversionJob job = existing.get();
        ArtifactDeletionReceipt receipt = requestReceipt(job.getTenantId(), jobId);
        resumeReceiptLocked(receipt);
    }

    private boolean resumeExistingReceiptForTenantLocked(UUID jobId, String tenantId) {
        Optional<ArtifactDeletionReceipt> existingReceipt = receiptStore.findByJobId(jobId);
        if (existingReceipt.isEmpty() || !existingReceipt.get().tenantId().equals(tenantId)) {
            return false;
        }
        resumeReceiptLocked(existingReceipt.get());
        return true;
    }

    private void resumeReceiptLocked(ArtifactDeletionReceipt candidate) {
        ArtifactDeletionReceipt receipt = receiptStore.findByJobId(candidate.jobId())
                .orElseThrow(() -> new IllegalStateException("artifact deletion receipt not found"));
        switch (receipt.state()) {
            case DELETION_REQUESTED -> resumeRequested(receipt);
            case METADATA_TOMBSTONED -> queueAndAttempt(receipt);
            case ARTIFACT_CLEANUP_PENDING -> attemptCleanup(receipt);
            case ARTIFACT_CLEANUP_FAILED -> queueAndAttempt(receipt);
            case ARTIFACT_CLEANUP_COMPLETED -> {
                // Completed receipts remain for idempotency and require no work.
            }
        }
    }

    private void resumeRequested(ArtifactDeletionReceipt receipt) {
        ArtifactDeletionReceipt exactReceipt = receipt;
        if (receipt.isArtifactChecksumPending()) {
            Optional<String> capturedChecksum = trySnapshotChecksum(receipt.jobId());
            if (capturedChecksum.isEmpty()) {
                return;
            }
            exactReceipt = receiptStore.recordArtifactChecksum(
                    receipt.jobId(),
                    capturedChecksum.orElseThrow(),
                    Instant.now()
            );
        }
        boolean deleted = repository.deleteByTenantAndId(exactReceipt.tenantId(), exactReceipt.jobId());
        if (!deleted && repository.findByTenantAndId(exactReceipt.tenantId(), exactReceipt.jobId()).isPresent()) {
            throw new IllegalStateException("tenant-scoped metadata tombstone was not applied");
        }
        ArtifactDeletionReceipt tombstoned = receiptStore.markMetadataTombstoned(
                exactReceipt.jobId(),
                Instant.now()
        );
        queueAndAttempt(tombstoned);
    }

    private Optional<String> trySnapshotChecksum(UUID jobId) {
        Optional<byte[]> artifact;
        try {
            artifact = Objects.requireNonNull(
                    artifactStore.getPdf(jobId),
                    "artifactStore.getPdf"
            );
        } catch (RuntimeException exception) {
            receiptStore.recordSnapshotFailure(jobId, FAILURE_READ, Instant.now());
            metrics.recordFailed();
            log.warn(
                    "Artifact deletion retained a pre-snapshot receipt. cause={}",
                    exception.getClass().getName()
            );
            return Optional.empty();
        }
        return Optional.of(artifact.map(ArtifactDeletionCoordinator::sha256)
                .orElse(ABSENT_ARTIFACT_CHECKSUM));
    }

    private void queueAndAttempt(ArtifactDeletionReceipt receipt) {
        ArtifactDeletionReceipt pending = receiptStore.markCleanupPending(
                receipt.jobId(),
                Instant.now()
        );
        attemptCleanup(pending);
    }

    private void attemptCleanup(ArtifactDeletionReceipt receipt) {
        Optional<byte[]> currentArtifact;
        try {
            currentArtifact = Objects.requireNonNull(
                    artifactStore.getPdf(receipt.jobId()),
                    "artifactStore.getPdf"
            );
        } catch (RuntimeException exception) {
            recordFailure(receipt, FAILURE_READ);
            return;
        }
        if (currentArtifact.isPresent()
                && !ABSENT_ARTIFACT_CHECKSUM.equals(receipt.artifactChecksum())
                && !receipt.artifactChecksum().equals(sha256(currentArtifact.orElseThrow()))) {
            recordFailure(receipt, FAILURE_CHECKSUM_MISMATCH);
            return;
        }
        try {
            artifactStore.deletePdf(receipt.jobId());
        } catch (RuntimeException exception) {
            recordFailure(receipt, FAILURE_DELETE);
            return;
        }
        receiptStore.markCleanupCompleted(receipt.jobId(), Instant.now());
        metrics.recordCompleted();
    }

    private void recordFailure(ArtifactDeletionReceipt receipt, String failureCode) {
        receiptStore.recordCleanupFailure(receipt.jobId(), failureCode, Instant.now());
        metrics.recordFailed();
    }

    private ArtifactDeletionReceipt requestReceipt(String tenantId, UUID jobId) {
        Optional<ArtifactDeletionReceipt> existing = receiptStore.findByJobId(jobId);
        if (existing.isPresent()) {
            ArtifactDeletionReceipt receipt = existing.get();
            if (!receipt.tenantId().equals(tenantId)) {
                throw new IllegalStateException("artifact deletion receipt conflicts with the active lifecycle");
            }
            return receipt;
        }
        UUID requestId = UUID.randomUUID();
        return receiptStore.request(
                requestId,
                tenantId,
                jobId,
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                "cleanup-v1:" + requestId.toString().replace("-", ""),
                Instant.now()
        );
    }

    private static void ensureSha256Available() {
        try {
            MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }
}
