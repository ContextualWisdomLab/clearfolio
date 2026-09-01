package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Production-boundary tests for tenant scoping inside the default conversion
 * application service.
 */
class DefaultDocumentConversionServiceTenantBoundaryTest {

    @Test
    void listReturnsOnlyTenantOwnedJobsAndNullContextReturnsEmpty() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingWorker worker = new RecordingWorker();
        DefaultDocumentConversionService service = service(repository, worker);
        ConversionJob tenantA = job("tenant-a", "a.pdf", "hash-a");
        ConversionJob tenantB = job("tenant-b", "b.pdf", "hash-b");
        repository.save(tenantA);
        repository.save(tenantB);

        List<ConversionJob> visible = toList(service.getJobsForTenant(context("tenant-a")));

        assertEquals(List.of(tenantA), visible);
        assertTrue(toList(service.getJobsForTenant(null)).isEmpty());
    }

    @Test
    void scopedRetryCollapsesMissingAndCrossTenantJobsToNotFound() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingWorker worker = new RecordingWorker();
        DefaultDocumentConversionService service = service(repository, worker);
        ConversionJob foreign = job("tenant-b", "foreign.pdf", "hash-foreign");
        assertTrue(foreign.markProcessing("attempt"));
        foreign.markDeadLettered("exhausted");
        repository.save(foreign);

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(foreign.getJobId(), "v1:audit", context("tenant-a"))
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(UUID.randomUUID(), "v1:audit", context("tenant-a"))
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(foreign.getJobId(), "v1:audit", null)
        );
        assertEquals(0, worker.enqueuedCount);
    }

    @Test
    void scopedRetryMutatesAndEnqueuesOnlyOwnedDeadLetteredJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingWorker worker = new RecordingWorker();
        DefaultDocumentConversionService service = service(repository, worker);
        ConversionJob owned = job("tenant-a", "owned.pdf", "hash-owned");
        assertTrue(owned.markProcessing("attempt"));
        owned.markDeadLettered("exhausted");
        repository.save(owned);

        RetryDeadLetterResult result = service.retryDeadLettered(
                owned.getJobId(),
                "v1:audit",
                context("tenant-a")
        );

        assertEquals(RetryDeadLetterResult.ACCEPTED, result);
        assertEquals(1, worker.enqueuedCount);
        assertEquals(owned.getJobId(), worker.lastJobId);
    }

    private static DefaultDocumentConversionService service(
            InMemoryConversionJobRepository repository,
            RecordingWorker worker) {
        return new DefaultDocumentConversionService(
                repository,
                file -> { },
                worker,
                new InMemoryArtifactStore(),
                new ConversionProperties()
        );
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "subject", Set.of());
    }

    private static ConversionJob job(String tenantId, String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                hash,
                10L,
                3
        );
    }

    private static List<ConversionJob> toList(Iterable<ConversionJob> jobs) {
        java.util.ArrayList<ConversionJob> result = new java.util.ArrayList<>();
        jobs.forEach(result::add);
        return result;
    }

    private static final class RecordingWorker implements ConversionWorker {
        private int enqueuedCount;
        private UUID lastJobId;

        @Override
        public void enqueue(UUID jobId) {
            enqueuedCount++;
            lastJobId = jobId;
        }
    }
}
