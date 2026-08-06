package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Verifies that one recovery pass cannot process beyond its configured bound.
 */
class ArtifactDeletionRecoveryBoundTest {

    @Test
    void recoverySelectsOnlyTheConfiguredNumberOfPendingReceipts() {
        ArtifactDeletionReceipt first = pendingReceipt(
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
        ArtifactDeletionReceipt second = pendingReceipt(
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        ArtifactDeletionReceipt third = pendingReceipt(
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );
        ArtifactDeletionReceiptStore store = mock(ArtifactDeletionReceiptStore.class);
        when(store.pendingReceipts()).thenReturn(List.of(first, second, third));
        when(store.findByJobId(first.jobId())).thenReturn(Optional.empty());
        when(store.findByJobId(second.jobId())).thenReturn(Optional.of(second));
        when(store.markCleanupCompleted(any(), any())).thenReturn(second);
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(store);
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                mock(ConversionJobRepository.class),
                new InMemoryArtifactStore(),
                store,
                metrics,
                new ArtifactLifecycleLockRegistry(8),
                2
        );

        assertEquals(2, coordinator.retryPendingWork());
        assertEquals(1L, metrics.failedAttempts());
        assertEquals(1L, metrics.completedAttempts());
        verify(store, never()).findByJobId(third.jobId());
    }

    private static ArtifactDeletionReceipt pendingReceipt(UUID jobId) {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.randomUUID(),
                "tenant-north",
                jobId,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:" + jobId.toString().replace("-", ""),
                now
        );
        ledger.markMetadataTombstoned(jobId, now);
        return ledger.markCleanupPending(jobId, now);
    }
}
