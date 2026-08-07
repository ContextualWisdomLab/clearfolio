package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

/**
 * Covers fail-closed coordinator branches through public deletion boundaries.
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

    @Test
    void deletionFailsBeforeReceiptAcceptanceWhenSha256IsUnavailable() {
        synchronized (SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> removedProviders =
                    SecurityProviderTestSupport.sha256ProviderPositions();
            removedProviders.stream()
                    .map(ProviderPosition::provider)
                    .map(Provider::getName)
                    .forEach(Security::removeProvider);
            try {
                UUID jobId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
                ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
                ConversionJobRepository repository = mock(ConversionJobRepository.class);
                when(repository.findByTenantAndId("tenant-edge", jobId))
                        .thenReturn(Optional.of(mock(ConversionJob.class)));
                ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                        repository,
                        mock(ArtifactStore.class),
                        ledger,
                        new ArtifactDeletionMetrics(ledger),
                        10
                );

                IllegalStateException unavailable = assertThrows(
                        IllegalStateException.class,
                        () -> coordinator.deleteForTenant(jobId, "tenant-edge")
                );

                assertEquals("SHA-256 digest unavailable", unavailable.getMessage());
                assertTrue(ledger.findByJobId(jobId).isEmpty());
            } finally {
                removedProviders.stream()
                        .sorted(Comparator.comparingInt(ProviderPosition::position))
                        .forEach(providerPosition -> Security.insertProviderAt(
                                providerPosition.provider(),
                                providerPosition.position()
                        ));
            }
        }
    }
}
