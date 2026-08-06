package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Verifies that the first artifact read is protected by durable recovery evidence.
 *
 * <p>The regression deliberately fails the initial checksum snapshot. The
 * authorized deletion request must remain recoverable across a process restart,
 * metadata must remain intact until an exact artifact digest can be bound, and
 * the later recovery pass must complete the same tenant-owned lifecycle.</p>
 */
class ArtifactDeletionInitialSnapshotRecoveryTest {

    private static final String TENANT_ID = "tenant-north";
    private static final UUID JOB_ID = UUID.fromString("35c19f28-4ad9-4d06-8c2f-17b7053101ee");
    private static final byte[] ARTIFACT = "%PDF-1.7\ninitial-read-recovery"
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void initialReadFailureIsDurableAndRecoversAfterRestart() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJob job = mock(ConversionJob.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(true);

        FailsFirstReadArtifactStore artifactStore = new FailsFirstReadArtifactStore(ARTIFACT);
        Path ledgerPath = tempDirectory.resolve("artifact-deletion-initial-read.log");
        ArtifactDeletionLedger firstLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionMetrics firstMetrics = new ArtifactDeletionMetrics(firstLedger);
        ArtifactDeletionCoordinator firstProcess = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                firstLedger,
                firstMetrics,
                100
        );

        assertTrue(firstProcess.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt retained = firstLedger.findByJobId(JOB_ID).orElseThrow();
        assertEquals("artifact_store_read_failed", retained.failureCode());
        assertEquals(1, retained.attemptCount());
        assertEquals(1L, firstMetrics.failedAttempts());
        assertEquals(1, firstMetrics.pendingReceipts());
        verify(repository, never()).deleteByTenantAndId(TENANT_ID, JOB_ID);

        ArtifactDeletionLedger restartedLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionMetrics restartedMetrics = new ArtifactDeletionMetrics(restartedLedger);
        ArtifactDeletionCoordinator restartedProcess = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                restartedLedger,
                restartedMetrics,
                100
        );

        assertEquals(1, restartedProcess.retryPendingWork());

        ArtifactDeletionReceipt completed = restartedLedger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completed.state());
        assertTrue(completed.artifactChecksum() != null && completed.artifactChecksum().matches("[0-9a-f]{64}"));
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        assertEquals(1L, restartedMetrics.completedAttempts());
        assertEquals(0, restartedMetrics.pendingReceipts());
        verify(repository).deleteByTenantAndId(TENANT_ID, JOB_ID);
    }

    /** Artifact store that fails exactly the first read and then behaves normally. */
    private static final class FailsFirstReadArtifactStore implements ArtifactStore {

        private byte[] bytes;
        private boolean failNextRead = true;

        private FailsFirstReadArtifactStore(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            bytes = Arrays.copyOf(pdfBytes, pdfBytes.length);
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            if (failNextRead) {
                failNextRead = false;
                throw new IllegalStateException("simulated initial artifact read failure");
            }
            return bytes == null ? Optional.empty() : Optional.of(Arrays.copyOf(bytes, bytes.length));
        }

        @Override
        public void deletePdf(UUID docId) {
            bytes = null;
        }
    }
}
