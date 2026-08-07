package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Proves that one permanently failing oldest receipt cannot starve newer
 * deletion work behind a bounded recovery batch.
 */
class ArtifactDeletionRecoveryFairnessTest {

    private static final UUID BLOCKED_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID READY_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void failedOldestSnapshotIsDeferredSoNewerCleanupRunsOnTheNextPass() {
        Instant requestedAt = Instant.parse("2026-08-07T00:00:00Z");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "tenant-north",
                BLOCKED_JOB_ID,
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                "cleanup-v1:blocked",
                requestedAt
        );
        ledger.request(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "tenant-north",
                READY_JOB_ID,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:ready",
                requestedAt.plusSeconds(1)
        );
        ledger.markMetadataTombstoned(READY_JOB_ID, requestedAt.plusSeconds(1));
        ledger.markCleanupPending(READY_JOB_ID, requestedAt.plusSeconds(1));

        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(ledger);
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                org.mockito.Mockito.mock(ConversionJobRepository.class),
                new BlockingOldestArtifactStore(),
                ledger,
                metrics,
                new ArtifactLifecycleLockRegistry(8),
                1
        );

        assertEquals(1, coordinator.retryPendingWork());
        assertEquals(ArtifactDeletionState.DELETION_REQUESTED,
                ledger.findByJobId(BLOCKED_JOB_ID).orElseThrow().state());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                ledger.findByJobId(READY_JOB_ID).orElseThrow().state());

        assertEquals(1, coordinator.retryPendingWork());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(READY_JOB_ID).orElseThrow().state());
        assertEquals(1L, metrics.failedAttempts());
        assertEquals(1L, metrics.completedAttempts());
    }

    /**
     * Simulates a permanently unavailable first artifact snapshot while every
     * other document reports a confirmed-absent artifact.
     */
    private static final class BlockingOldestArtifactStore implements ArtifactStore {

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            throw new UnsupportedOperationException("test store does not accept writes");
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            if (BLOCKED_JOB_ID.equals(docId)) {
                throw new IllegalStateException("controlled snapshot failure");
            }
            return Optional.empty();
        }

        @Override
        public void deletePdf(UUID docId) {
            // Confirmed-absent artifacts need no physical deletion in this fixture.
        }
    }
}
