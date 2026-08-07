package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Verifies idempotent recovery through the legacy global deletion boundary.
 */
class ArtifactDeletionGlobalRetryTest {

    @Test
    void repeatedGlobalDeleteResumesFailedCleanupAfterMetadataIsTombstoned() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(new ConversionJob(
                jobId,
                "tenant-north",
                "subject-north",
                "report.pdf",
                "application/pdf",
                "content-hash",
                3L,
                3
        ));
        FailingOnceDeleteStore artifactStore = new FailingOnceDeleteStore();
        artifactStore.putPdf(jobId, new byte[] {1, 2, 3});
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger),
                10
        );

        coordinator.deleteGlobally(jobId);

        assertTrue(repository.findById(jobId).isEmpty());
        assertTrue(artifactStore.getPdf(jobId).isPresent());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                ledger.findByJobId(jobId).orElseThrow().state()
        );

        coordinator.deleteGlobally(jobId);

        assertTrue(artifactStore.getPdf(jobId).isEmpty());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(jobId).orElseThrow().state()
        );
    }

    private static final class FailingOnceDeleteStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final AtomicBoolean failNextDelete = new AtomicBoolean(true);

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
            if (failNextDelete.compareAndSet(true, false)) {
                throw new IllegalStateException("controlled delete failure");
            }
            delegate.deletePdf(docId);
        }
    }
}
