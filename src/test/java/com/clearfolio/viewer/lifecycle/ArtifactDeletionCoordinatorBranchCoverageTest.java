package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Covers the fail-closed tenant conflict for an already active deletion
 * lifecycle through the public coordinator boundary.
 */
class ArtifactDeletionCoordinatorBranchCoverageTest {

    @Test
    void existingReceiptRejectsAJobPresentedAsAnotherTenant() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "tenant-edge",
                jobId,
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                "audit-v1:0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-06T12:00:00Z")
        );
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId("tenant-other", jobId))
                .thenReturn(Optional.of(mock(ConversionJob.class)));
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                repository,
                mock(ArtifactStore.class),
                ledger,
                new ArtifactDeletionMetrics(ledger),
                10
        );

        IllegalStateException conflict = assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(jobId, "tenant-other")
        );

        assertEquals(
                "artifact deletion receipt conflicts with the active lifecycle",
                conflict.getMessage()
        );
    }
}
