package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Regression coverage for receipt-first recovery when the very first artifact
 * read fails before an exact cleanup digest can be captured.
 */
class ArtifactDeletionInitialReadRecoveryTest {

    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-north";
    private static final byte[] ARTIFACT = "%PDF-1.7\ninitial-read-recovery".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void initialArtifactReadFailurePersistsRetryIntentAndRecoversAfterRestart() {
        Path ledgerPath = tempDirectory.resolve("artifact-deletion-receipts.log");
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(new ConversionJob(
                JOB_ID,
                TENANT_ID,
                "subject-north",
                "board-pack.pdf",
                "application/pdf",
                "initial-read-recovery-hash",
                ARTIFACT.length,
                3
        ));
        FailingFirstReadArtifactStore artifactStore = new FailingFirstReadArtifactStore();
        artifactStore.putPdf(JOB_ID, ARTIFACT);
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
        assertEquals(ArtifactDeletionState.DELETION_REQUESTED, retained.state());
        assertEquals(ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM, retained.artifactChecksum());
        assertTrue(repository.findByTenantAndId(TENANT_ID, JOB_ID).isPresent());
        assertEquals(1L, firstMetrics.failedAttempts());
        assertEquals(1, firstMetrics.pendingReceipts());

        ArtifactDeletionLedger restartedLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionMetrics restartedMetrics = new ArtifactDeletionMetrics(restartedLedger);
        ArtifactDeletionCoordinator restartedProcess = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                restartedLedger,
                restartedMetrics,
                100
        );

        restartedProcess.recoverPendingAfterStartup();

        ArtifactDeletionReceipt completed = restartedLedger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completed.state());
        assertEquals(0, restartedMetrics.pendingReceipts());
        assertEquals(1L, restartedMetrics.completedAttempts());
        assertTrue(repository.findByTenantAndId(TENANT_ID, JOB_ID).isEmpty());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
    }

    /**
     * Fails exactly the first read while retaining the original artifact bytes
     * for a later recovery pass.
     */
    private static final class FailingFirstReadArtifactStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final AtomicBoolean failNextRead = new AtomicBoolean(true);

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            delegate.putPdf(docId, pdfBytes);
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            if (failNextRead.compareAndSet(true, false)) {
                throw new IllegalStateException("artifact read failed");
            }
            return delegate.getPdf(docId);
        }

        @Override
        public void deletePdf(UUID docId) {
            delegate.deletePdf(docId);
        }
    }
}
