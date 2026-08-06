package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Covers independent tenant and artifact-generation conflict checks for an
 * already active deletion lifecycle.
 */
class ArtifactDeletionCoordinatorBranchCoverageTest {

    @Test
    void existingTenantReceiptRejectsAChangedArtifactChecksum() throws Exception {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String originalChecksum =
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String changedChecksum =
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "tenant-edge",
                jobId,
                originalChecksum,
                "audit-v1:0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-06T12:00:00Z")
        );
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                mock(ConversionJobRepository.class),
                mock(ArtifactStore.class),
                ledger,
                new ArtifactDeletionMetrics(ledger),
                10
        );
        Method requestReceipt = ArtifactDeletionCoordinator.class.getDeclaredMethod(
                "requestReceipt",
                String.class,
                UUID.class,
                String.class
        );
        requestReceipt.setAccessible(true);

        InvocationTargetException invocation = assertThrows(
                InvocationTargetException.class,
                () -> requestReceipt.invoke(coordinator, "tenant-edge", jobId, changedChecksum)
        );

        assertEquals(
                "artifact deletion receipt conflicts with the active lifecycle",
                invocation.getCause().getMessage()
        );
    }
}
