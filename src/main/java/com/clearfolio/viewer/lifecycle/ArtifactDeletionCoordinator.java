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
 * <p>Administrative deletion is intentionally serialized because it is a
 * low-frequency control-plane operation and each receipt transition must be
 * observed in order. Replaceable durable adapters may use database transactions,
 * compare-and-set state, or a single-consumer outbox while preserving this
 * interface's tenant, permanent-job-identity, digest, retry, and fail-closed
 * semantics.</p>
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
    private final int maxReceiptsPerRun;

    /**
     * Creates the coordinator and validates the recovery batch bound.
     *
     * @param repository conversion-job metadata repository
     * @param artifactStore document artifact store
     * @param receiptStore durable deletion receipt store
     * @param metrics low-cardinality cleanup metrics
     * @param maxReceiptsPerRun maximum incomplete receipts processed in one pass
     * @throws IllegalArgumentException when the recovery batch bound is not positive
     */
    @Autowired
    public ArtifactDeletionCoordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics,
            @Value("${clearfolio.artifact-deletion-cleanup.max-receipts-per-run:100}")
            int maxReceiptsPerRun
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.receiptStore = Objects.requireNonNull(receiptStore, "receiptStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (maxReceiptsPerRun <= 0) {
            throw new IllegalArgumentException("maxReceiptsPerRun must be positive");
        }
        this.maxReceiptsPerRun = maxReceiptsPerRun;
    }

    /**
     * Deletes one tenant-owned job and starts durable artifact cleanup.
     *
     * <p>The method returns {@code true} after the tenant-owned metadata is
     * tombstoned. Artifact-store failure is retained as a retryable receipt and
     * does not erase the authorized metadata result.</p>
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param tenantId authenticated tenant identifier
     * @return true when an owned job entered the durable deletion lifecycle
     */
    public synchronized boolean deleteForTenant(UUID jobId, String tenantId) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        String requiredTenantId = Objects.requireNonNull(tenantId, "tenantId").strip();
        Optional<ConversionJob> existing = repository.findByTenantAndId(requiredTenantId, requiredJobId);
        if (existing.isEmpty()) {
            return false;
        }

        ArtifactDeletionReceipt receipt = requestReceipt(
                requiredTenantId,
                requiredJobId,
                snapshotChecksum(requiredJobId)
        );
        resumeReceipt(receipt);
        return true;
    }

    /**
     * Preserves the legacy unscoped deletion entry point while using durable
     * receipts whenever metadata still identifies the owning job lifecycle.
     *
     * @param jobId permanently reserved conversion-job identifier
     */
    public synchronized void deleteGlobally(UUID jobId) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Optional<ConversionJob> existing = repository.findById(requiredJobId);
        if (existing.isEmpty()) {
            repository.deleteById(requiredJobId);
            return;
        }

        ConversionJob job = existing.get();
        ArtifactDeletionReceipt receipt = requestReceipt(
                job.getTenantId(),
                requiredJobId,
                snapshotChecksum(requiredJobId)
        );
        repository.deleteById(requiredJobId);
        ArtifactDeletionReceipt tombstoned = receiptStore.markMetadataTombstoned(
                requiredJobId,
                Instant.now()
        );
        queueAndAttempt(tombstoned);
    }

    /**
     * Replays one bounded deterministic batch of incomplete deletion receipts.
     *
     * <p>A failure in one receipt is isolated so later receipts in the same
     * bounded batch remain eligible. The receipt itself remains the authoritative
     * recovery evidence.</p>
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
                log.warn("Artifact deletion recovery retained an incomplete receipt.");
            }
        }
        return selected;
    }

    /**
     * Replays incomplete receipts when Spring reports that the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingAfterStartup() {
        retryPendingWork();
    }

    /**
     * Replays a bounded receipt batch after the configured fixed delay.
     */
    @Scheduled(fixedDelayString = "${clearfolio.artifact-deletion-cleanup.retry-delay-ms:30000}")
    public void retryPendingAfterDelay() {
        retryPendingWork();
    }

    void resumeReceipt(ArtifactDeletionReceipt candidate) {
        ArtifactDeletionReceipt receipt = receiptStore.findByJobId(
                Objects.requireNonNull(candidate, "candidate").jobId()
        ).orElseThrow(() -> new IllegalStateException("artifact deletion receipt not found"));
        switch (receipt.state()) {
            case DELETION_REQUESTED -> resumeRequested(receipt);
            case METADATA_TOMBSTONED -> queueAndAttempt(receipt);
            case ARTIFACT_CLEANUP_PENDING -> attemptCleanup(receipt);
            case ARTIFACT_CLEANUP_FAILED -> queueAndAttempt(receipt);
            case ARTIFACT_CLEANUP_COMPLETED -> {
                // Completed receipts are retained for idempotency but require no work.
            }
        }
    }

    private void resumeRequested(ArtifactDeletionReceipt receipt) {
        boolean deleted = repository.deleteByTenantAndId(receipt.tenantId(), receipt.jobId());
        if (!deleted && repository.findByTenantAndId(receipt.tenantId(), receipt.jobId()).isPresent()) {
            throw new IllegalStateException("tenant-scoped metadata tombstone was not applied");
        }
        ArtifactDeletionReceipt tombstoned = receiptStore.markMetadataTombstoned(
                receipt.jobId(),
                Instant.now()
        );
        queueAndAttempt(tombstoned);
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

    private ArtifactDeletionReceipt requestReceipt(
            String tenantId,
            UUID jobId,
            String artifactChecksum
    ) {
        Optional<ArtifactDeletionReceipt> existing = receiptStore.findByJobId(jobId);
        if (existing.isPresent()) {
            ArtifactDeletionReceipt receipt = existing.get();
            if (!receipt.tenantId().equals(tenantId)
                    || !receipt.artifactChecksum().equals(artifactChecksum)) {
                throw new IllegalStateException("artifact deletion receipt conflicts with the active lifecycle");
            }
            return receipt;
        }

        UUID requestId = UUID.randomUUID();
        return receiptStore.request(
                requestId,
                tenantId,
                jobId,
                artifactChecksum,
                "cleanup-v1:" + requestId.toString().replace("-", ""),
                Instant.now()
        );
    }

    private String snapshotChecksum(UUID jobId) {
        Optional<byte[]> artifact = Objects.requireNonNull(
                artifactStore.getPdf(jobId),
                "artifactStore.getPdf"
        );
        return artifact.map(ArtifactDeletionCoordinator::sha256)
                .orElse(ABSENT_ARTIFACT_CHECKSUM);
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
