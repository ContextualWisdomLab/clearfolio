package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Concurrency regression for administrator deletion racing artifact publication.
 */
class AdminDeletionArtifactRaceTest {

    @Test
    void deletingClaimedJobCannotLeaveLateArtifact() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        ConversionProperties properties = new ConversionProperties();
        properties.setMaxRetryAttempts(1);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "subject-a",
                "report.docx",
                "application/octet-stream",
                "hash-delete-race",
                100L,
                1
        );
        repository.save(job);

        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch allowPublication = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DefaultConversionWorker worker = new DefaultConversionWorker(
                    repository,
                    repository,
                    executor,
                    artifactStore,
                    ignored -> {
                        generationStarted.countDown();
                        try {
                            if (!allowPublication.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("publication gate timed out");
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("publication interrupted", ex);
                        }
                        return "%PDF-1.7\nrace".getBytes(StandardCharsets.US_ASCII);
                    },
                    properties
            );
            DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                    repository,
                    repository,
                    file -> { },
                    worker,
                    artifactStore,
                    properties
            );

            worker.enqueue(jobId);
            assertTrue(generationStarted.await(5, TimeUnit.SECONDS));
            assertTrue(service.deleteJob(
                    jobId,
                    new TenantContext("tenant-a", "operator-a", Set.of())
            ));
            allowPublication.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(repository.findById(jobId).isEmpty());
            assertFalse(artifactStore.getPdf(jobId).isPresent());
        } finally {
            allowPublication.countDown();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }
}
