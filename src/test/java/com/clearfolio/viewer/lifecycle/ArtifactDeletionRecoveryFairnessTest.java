package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Proves that one permanently failing oldest receipt cannot starve newer
 * deletion work behind a bounded recovery batch, including across restart.
 */
class ArtifactDeletionRecoveryFairnessTest {

    private static final UUID BLOCKED_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID READY_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDirectory;

    @Test
    void failedOldestSnapshotIsDeferredDurablySoNewerCleanupRunsAfterRestart() {
        Instant requestedAt = Instant.parse("2026-08-07T00:00:00Z");
        Path ledgerPath = tempDirectory.resolve("artifact-deletion-receipts.log");
        ArtifactDeletionLedger firstLedger = new ArtifactDeletionLedger(ledgerPath);
        firstLedger.request(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "tenant-north",
                BLOCKED_JOB_ID,
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                "cleanup-v1:blocked",
                requestedAt
        );
        firstLedger.request(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "tenant-north",
                READY_JOB_ID,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:ready",
                requestedAt.plusSeconds(1)
        );
        firstLedger.markMetadataTombstoned(READY_JOB_ID, requestedAt.plusSeconds(1));
        firstLedger.markCleanupPending(READY_JOB_ID, requestedAt.plusSeconds(1));

        ArtifactStore artifactStore = new BlockingOldestArtifactStore();
        ArtifactDeletionMetrics firstMetrics = new ArtifactDeletionMetrics(firstLedger);
        ArtifactDeletionCoordinator firstProcess = coordinator(
                artifactStore,
                firstLedger,
                firstMetrics
        );

        assertEquals(1, firstProcess.retryPendingWork());
        ArtifactDeletionReceipt deferred = firstLedger.findByJobId(BLOCKED_JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.DELETION_REQUESTED, deferred.state());
        assertEquals(1, deferred.attemptCount());
        assertNotNull(deferred.lastAttemptAt());
        assertEquals("artifact_store_read_failed", deferred.failureCode());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                firstLedger.findByJobId(READY_JOB_ID).orElseThrow().state());
        assertEquals(1L, firstMetrics.failedAttempts());

        ArtifactDeletionLedger restartedLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionMetrics restartedMetrics = new ArtifactDeletionMetrics(restartedLedger);
        ArtifactDeletionCoordinator restartedProcess = coordinator(
                artifactStore,
                restartedLedger,
                restartedMetrics
        );

        assertEquals(1, restartedProcess.retryPendingWork());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                restartedLedger.findByJobId(READY_JOB_ID).orElseThrow().state());
        assertEquals(0L, restartedMetrics.failedAttempts());
        assertEquals(1L, restartedMetrics.completedAttempts());
    }

    private static ArtifactDeletionCoordinator coordinator(
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics
    ) {
        return new ArtifactDeletionCoordinator(
                org.mockito.Mockito.mock(ConversionJobRepository.class),
                artifactStore,
                receiptStore,
                metrics,
                new ArtifactLifecycleLockRegistry(8),
                1
        );
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
