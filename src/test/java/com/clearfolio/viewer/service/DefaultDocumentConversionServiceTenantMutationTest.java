package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Service-level regressions for atomic tenant-scoped administrator mutations.
 */
class DefaultDocumentConversionServiceTenantMutationTest {

    @Test
    void tenantScopedDeleteMutatesOnlyTheOwningTenant() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        DefaultDocumentConversionService service = service(repository, id -> { });
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId, "tenant-a", "hash-delete-service"));

        assertFalse(service.deleteJob(jobId, context("tenant-b")));
        assertTrue(repository.findById(jobId).isPresent());
        assertTrue(service.deleteJob(jobId, context("tenant-a")));
        assertTrue(repository.findById(jobId).isEmpty());
    }

    @Test
    void tenantScopedRetryReturnsNotFoundForWrongTenantAndEnqueuesOwner() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        AtomicReference<UUID> enqueued = new AtomicReference<>();
        DefaultDocumentConversionService service = service(repository, enqueued::set);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, "tenant-a", "hash-retry-service");
        repository.save(job);
        repository.markDeadLettered(jobId, "failed");

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(jobId, context("tenant-b"), "operator")
        );
        assertEquals(ConversionJobStatus.DEAD_LETTERED, job.getStatus());
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(jobId, context("tenant-a"), "operator")
        );
        assertEquals(jobId, enqueued.get());
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
    }

    @Test
    void tenantScopedRetryMapsIneligibleAndNullContextWithoutMutation() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        DefaultDocumentConversionService service = service(repository, id -> { });
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId, "tenant-a", "hash-ineligible-service"));

        assertEquals(
                RetryDeadLetterResult.NOT_ELIGIBLE,
                service.retryDeadLettered(jobId, context("tenant-a"), "operator")
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(jobId, null, "operator")
        );
    }

    private static DefaultDocumentConversionService service(
            InMemoryConversionJobRepository repository,
            ConversionWorker worker
    ) {
        return new DefaultDocumentConversionService(
                repository,
                repository,
                file -> { },
                worker,
                new InMemoryArtifactStore(),
                new ConversionProperties()
        );
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "subject-a", Set.of());
    }

    private static ConversionJob job(UUID jobId, String tenantId, String contentHash) {
        return new ConversionJob(
                jobId,
                tenantId,
                "subject-a",
                "report.pdf",
                "application/pdf",
                contentHash,
                100L,
                3
        );
    }
}
