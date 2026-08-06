package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.clearfolio.viewer.artifact.PdfBoxArtifactGenerator;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionCoordinator;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionLedger;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionMetrics;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionState;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Verifies that deletion fences an artifact write already in flight.
 */
class DefaultConversionWorkerDeletionRaceTest {

    @Test
    void deletionWaitsForInFlightConversionThenRemovesTheFinalBytes() throws Exception {
        String tenantId = "tenant-north";
        UUID jobId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(new ConversionJob(
                jobId,
                tenantId,
                "subject-north",
                "late-write.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "late-write-content-hash",
                42L,
                1
        ));
        BlockingBeforeWriteArtifactStore artifactStore = new BlockingBeforeWriteArtifactStore();
        ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService deletionExecutor = Executors.newSingleThreadExecutor();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(registry, ledger),
                100
        );
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                workerExecutor,
                artifactStore,
                new PdfBoxArtifactGenerator(),
                new ConversionProperties()
        );

        try {
            worker.enqueue(jobId);
            assertTrue(artifactStore.writeEntered().await(5, TimeUnit.SECONDS));

            CountDownLatch deletionStarted = new CountDownLatch(1);
            Future<Boolean> deletion = deletionExecutor.submit(() -> {
                deletionStarted.countDown();
                return coordinator.deleteForTenant(jobId, tenantId);
            });
            assertTrue(deletionStarted.await(5, TimeUnit.SECONDS));
            assertFalse(deletion.isDone());

            artifactStore.releaseWrite().countDown();

            assertTrue(deletion.get(5, TimeUnit.SECONDS));
            assertTrue(repository.findById(jobId).isEmpty());
            assertTrue(artifactStore.getPdf(jobId).isEmpty());
            assertEquals(
                    ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                    ledger.findByJobId(jobId).orElseThrow().state()
            );
            assertEquals(1.0, registry.get("clearfolio.artifact.deletion.attempts")
                    .tag("outcome", "completed")
                    .counter()
                    .count());
        } finally {
            artifactStore.releaseWrite().countDown();
            workerExecutor.shutdownNow();
            deletionExecutor.shutdownNow();
            assertTrue(workerExecutor.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(deletionExecutor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class BlockingBeforeWriteArtifactStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final CountDownLatch writeEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWrite = new CountDownLatch(1);

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            writeEntered.countDown();
            await(releaseWrite);
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

        private CountDownLatch writeEntered() {
            return writeEntered;
        }

        private CountDownLatch releaseWrite() {
            return releaseWrite;
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("artifact write latch timeout");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("artifact write interrupted", exception);
            }
        }
    }
}
