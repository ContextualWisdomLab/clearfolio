package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.artifact.LifecycleFencedArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Proves that artifact publication and deletion use one per-job lifecycle fence.
 */
class ArtifactLifecycleSerializationTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void deletionWaitsForInFlightPublicationThenDeletesThePublishedGeneration() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            UUID jobId = UUID.randomUUID();
            ArtifactLifecycleLockRegistry locks = new ArtifactLifecycleLockRegistry();
            ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
            BlockingPutStore delegate = new BlockingPutStore();
            ArtifactStore artifactStore = new LifecycleFencedArtifactStore(
                    delegate,
                    ledger,
                    locks
            );
            InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
            repository.save(new ConversionJob(
                    jobId,
                    "tenant-a",
                    "subject-a",
                    "report.pdf",
                    "application/pdf",
                    "serialization-hash",
                    3L,
                    3
            ));
            ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                    repository,
                    artifactStore,
                    ledger,
                    new ArtifactDeletionMetrics(ledger),
                    locks,
                    100
            );
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> publication = executor.submit(
                        () -> artifactStore.putPdf(jobId, new byte[] {1, 2, 3})
                );
                assertTrue(delegate.putEntered.await(2, TimeUnit.SECONDS));

                Future<Boolean> deletion = executor.submit(
                        () -> coordinator.deleteForTenant(jobId, "tenant-a")
                );
                assertFalse(deletion.isDone());

                delegate.releasePut.countDown();
                publication.get(2, TimeUnit.SECONDS);
                assertTrue(deletion.get(2, TimeUnit.SECONDS));
            } finally {
                delegate.releasePut.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }

            assertTrue(repository.findById(jobId).isEmpty());
            assertTrue(artifactStore.getPdf(jobId).isEmpty());
            assertEquals(
                    ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                    ledger.findByJobId(jobId).orElseThrow().state()
            );
        });
    }

    private static final class BlockingPutStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final CountDownLatch putEntered = new CountDownLatch(1);
        private final CountDownLatch releasePut = new CountDownLatch(1);

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            putEntered.countDown();
            await(releasePut);
            delegate.putPdf(docId, pdfBytes);
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            return delegate.getPdf(docId);
        }

        @Override
        public void deletePdf(UUID docId) {
            delegate.deletePdf(docId);
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("publication release was not signalled");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("publication was interrupted", exception);
            }
        }
    }
}
