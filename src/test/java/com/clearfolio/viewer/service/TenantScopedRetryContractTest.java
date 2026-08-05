package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Verifies the compatibility and durable-service contracts for tenant-scoped
 * dead-letter retry operations.
 */
class TenantScopedRetryContractTest {

    @Test
    void interfaceDefaultRejectsUnownedJobsBeforeLegacyMutation() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, "tenant-a", "default-contract");
        AtomicReference<UUID> retriedJobId = new AtomicReference<>();
        AtomicReference<String> retriedOperatorId = new AtomicReference<>();
        DocumentConversionService service = new DocumentConversionService() {
            @Override
            public UUID submit(MultipartFile file) {
                return UUID.randomUUID();
            }

            @Override
            public Optional<ConversionJob> getJob(UUID requestedJobId) {
                return jobId.equals(requestedJobId) ? Optional.of(job) : Optional.empty();
            }

            @Override
            public RetryDeadLetterResult retryDeadLettered(UUID requestedJobId, String operatorId) {
                retriedJobId.set(requestedJobId);
                retriedOperatorId.set(operatorId);
                return RetryDeadLetterResult.ACCEPTED;
            }

            @Override
            public void deleteJob(UUID requestedJobId) {
            }

            @Override
            public Iterable<ConversionJob> getAllJobs() {
                return java.util.List.of(job);
            }
        };
        TenantContext tenantA = new TenantContext("tenant-a", "subject-a", Set.of());
        TenantContext tenantB = new TenantContext("tenant-b", "subject-b", Set.of());

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(jobId, null, "operator-null")
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(UUID.randomUUID(), tenantA, "operator-missing")
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(jobId, tenantB, "operator-cross-tenant")
        );
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(jobId, tenantA, "operator-owned")
        );
        assertEquals(jobId, retriedJobId.get());
        assertEquals("operator-owned", retriedOperatorId.get());
    }

    @Test
    void durableServiceRejectsNullMissingAndCrossTenantJobsWithoutMutation() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = service(repository, worker);
        ConversionJob job = deadLetteredJob(UUID.randomUUID(), "tenant-a", "durable-reject");
        repository.save(job);

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(job.getJobId(), null, "operator-null")
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(
                        UUID.randomUUID(),
                        new TenantContext("tenant-a", "subject-a", Set.of()),
                        "operator-missing"
                )
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(
                        job.getJobId(),
                        new TenantContext("tenant-b", "subject-b", Set.of()),
                        "operator-cross-tenant"
                )
        );
        assertEquals(ConversionJobStatus.FAILED, job.getStatus());
        assertTrue(job.isDeadLettered());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void durableServiceMapsOwnedEligibilityAndAcceptsOwnedDeadLetteredJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = service(repository, worker);
        TenantContext tenant = new TenantContext("tenant-a", "subject-a", Set.of());
        ConversionJob active = job(UUID.randomUUID(), "tenant-a", "durable-active");
        ConversionJob deadLettered = deadLetteredJob(
                UUID.randomUUID(),
                "tenant-a",
                "durable-accepted"
        );
        repository.save(active);
        repository.save(deadLettered);

        assertEquals(
                RetryDeadLetterResult.NOT_ELIGIBLE,
                service.retryDeadLettered(active.getJobId(), tenant, "operator-active")
        );
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(deadLettered.getJobId(), tenant, "operator-owned")
        );
        assertEquals(ConversionJobStatus.SUBMITTED, deadLettered.getStatus());
        assertTrue(deadLettered.getStatusMessage().contains("operator-owned"));
        assertEquals(1, worker.enqueuedCount());
        assertEquals(deadLettered.getJobId(), worker.lastEnqueuedJobId());
    }

    private static DocumentConversionService service(
            InMemoryConversionJobRepository repository,
            RecordingConversionWorker worker
    ) {
        return new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new InMemoryArtifactStore(),
                new ConversionProperties()
        );
    }

    private static ConversionJob deadLetteredJob(UUID jobId, String tenantId, String hash) {
        ConversionJob job = job(jobId, tenantId, hash);
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        return job;
    }

    private static ConversionJob job(UUID jobId, String tenantId, String hash) {
        return new ConversionJob(
                jobId,
                tenantId,
                "subject-a",
                "contract.docx",
                "application/octet-stream",
                hash,
                1L,
                3
        );
    }

    private static final class RecordingConversionWorker implements ConversionWorker {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<UUID> lastJobId = new AtomicReference<>();

        @Override
        public void enqueue(UUID jobId) {
            lastJobId.set(jobId);
            count.incrementAndGet();
        }

        int enqueuedCount() {
            return count.get();
        }

        UUID lastEnqueuedJobId() {
            return lastJobId.get();
        }
    }
}
