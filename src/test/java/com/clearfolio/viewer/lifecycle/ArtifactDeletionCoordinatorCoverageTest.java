package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Covers idempotency and defensive branches that require narrowly controlled
 * repository and receipt-store state.
 */
class ArtifactDeletionCoordinatorCoverageTest {

    private static final String TENANT_ID = "tenant-north";
    private static final String OTHER_TENANT_ID = "tenant-south";
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final byte[] ARTIFACT = "%PDF-1.7\nprivate".getBytes(StandardCharsets.UTF_8);

    @Test
    void repeatedTenantDeletionUsesCompletedReceiptAfterMetadataIsGone() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        CountingArtifactStore artifactStore = new CountingArtifactStore();
        artifactStore.putPdf(JOB_ID, ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(ledger);
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                metrics
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));
        ArtifactDeletionReceipt firstReceipt = ledger.findByJobId(JOB_ID).orElseThrow();
        assertTrue(firstReceipt.isCompleted());
        assertTrue(repository.findById(JOB_ID).isEmpty());

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        assertSame(firstReceipt, ledger.findByJobId(JOB_ID).orElseThrow());
        assertEquals(1, artifactStore.deleteCalls());
        assertEquals(1L, metrics.completedAttempts());
        assertEquals(0, metrics.pendingReceipts());
    }

    @Test
    void existingMatchingRequestedReceiptResumesWithoutCreatingAnotherIdentity() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt requested = ledger.request(
                UUID.randomUUID(),
                TENANT_ID,
                JOB_ID,
                sha256(ARTIFACT),
                "cleanup-v1:existing",
                Instant.now()
        );
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger)
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt completed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(requested.requestId(), completed.requestId());
        assertTrue(completed.isCompleted());
    }

    @Test
    void receiptForAnotherTenantRemainsConcealedWhenMetadataIsGone() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt receipt = ledger.request(
                UUID.randomUUID(),
                OTHER_TENANT_ID,
                JOB_ID,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:other-tenant",
                Instant.now()
        );
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                new InMemoryArtifactStore(),
                ledger,
                new ArtifactDeletionMetrics(ledger)
        );

        assertFalse(coordinator.deleteForTenant(JOB_ID, TENANT_ID));
        assertSame(receipt, ledger.findByJobId(JOB_ID).orElseThrow());
    }

    @Test
    void metadataTombstoneFailureLeavesRequestedReceiptForRecovery() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(false);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
        );

        assertEquals("tenant-scoped metadata tombstone was not applied", exception.getMessage());
        assertEquals(
                ArtifactDeletionState.DELETION_REQUESTED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(JOB_ID).isPresent());
    }

    @Test
    void aggregateMetricsExposeOnlyCountsAndCurrentPendingWork() {
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        when(receiptStore.pendingCount()).thenReturn(3);
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(receiptStore);

        metrics.recordCompleted();
        metrics.recordFailed();
        metrics.recordFailed();

        assertEquals(1L, metrics.completedAttempts());
        assertEquals(2L, metrics.failedAttempts());
        assertEquals(3, metrics.pendingReceipts());
        assertThrows(NullPointerException.class, () -> new ArtifactDeletionMetrics(null));
    }

    private static ArtifactDeletionCoordinator coordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics
    ) {
        return new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                receiptStore,
                metrics,
                100
        );
    }

    private static InMemoryConversionJobRepository repositoryWithJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(job());
        return repository;
    }

    private static ConversionJob job() {
        return new ConversionJob(
                JOB_ID,
                TENANT_ID,
                "subject-north",
                "report.pdf",
                "application/pdf",
                "artifact-cleanup-hash",
                ARTIFACT.length,
                3
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class CountingArtifactStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final AtomicInteger deleteCalls = new AtomicInteger();

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            delegate.putPdf(docId, pdfBytes);
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            return delegate.getPdf(docId);
        }

        @Override
        public void deletePdf(UUID docId) {
            deleteCalls.incrementAndGet();
            delegate.deletePdf(docId);
        }

        private int deleteCalls() {
            return deleteCalls.get();
        }
    }
}
