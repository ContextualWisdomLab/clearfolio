package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Verifies the compatibility and durable-service contracts for tenant-scoped
 * dead-letter retry operations.
 */
class TenantScopedRetryContractTest {

    private static final Duration CONCURRENCY_TEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void interfaceDefaultFailsClosedWithoutLookupOrLegacyMutation() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, "tenant-a", "default-contract");
        AtomicInteger getJobCalls = new AtomicInteger();
        AtomicReference<UUID> retriedJobId = new AtomicReference<>();
        AtomicReference<String> retriedOperatorId = new AtomicReference<>();
        DocumentConversionService service = new DocumentConversionService() {
            @Override
            public UUID submit(MultipartFile file) {
                return UUID.randomUUID();
            }

            @Override
            public Optional<ConversionJob> getJob(UUID requestedJobId) {
                getJobCalls.incrementAndGet();
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
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(jobId, tenantA, "operator-owned")
        );
        assertEquals(0, getJobCalls.get());
        assertNull(retriedJobId.get());
        assertNull(retriedOperatorId.get());
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

    @Test
    void acceptedRetryCannotEnqueueSameIdentifierReplacementOwnedByAnotherTenant() {
        assertTimeoutPreemptively(CONCURRENCY_TEST_TIMEOUT, () -> {
            InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
            UUID sharedJobId = UUID.randomUUID();
            ConversionJob original = deadLetteredJob(sharedJobId, "tenant-a", "original-hash");
            ConversionJob replacement = job(sharedJobId, "tenant-b", "replacement-hash");
            repository.save(original);

            CountDownLatch retryTransitioned = new CountDownLatch(1);
            CountDownLatch releaseRetry = new CountDownLatch(1);
            BlockingRetryStateStore stateStore = new BlockingRetryStateStore(
                    repository,
                    retryTransitioned,
                    releaseRetry
            );
            ObservingConversionWorker worker = new ObservingConversionWorker(repository);
            DocumentConversionService service = new DefaultDocumentConversionService(
                    repository,
                    stateStore,
                    new DefaultDocumentValidationService(new ConversionProperties()),
                    worker,
                    new InMemoryArtifactStore(),
                    new ConversionProperties()
            );
            TenantContext tenantA = new TenantContext("tenant-a", "subject-a", Set.of());

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<RetryDeadLetterResult> retryResult = executor.submit(
                        () -> service.retryDeadLettered(
                                sharedJobId,
                                tenantA,
                                "operator-owned"
                        )
                );
                assertTrue(
                        retryTransitioned.await(2, TimeUnit.SECONDS),
                        "tenant retry did not reach the accepted transition boundary"
                );

                try {
                    assertThrows(
                            IllegalStateException.class,
                            () -> repository.save(replacement),
                            "a live conversion job identifier must not be rebound to another tenant"
                    );
                } finally {
                    releaseRetry.countDown();
                }

                assertEquals(
                        RetryDeadLetterResult.ACCEPTED,
                        retryResult.get(2, TimeUnit.SECONDS)
                );
                assertEquals(1, worker.enqueuedCount());
                assertSame(original, worker.lastEnqueuedJob());
                assertEquals("tenant-a", worker.lastEnqueuedJob().getTenantId());
                assertTrue(
                        repository.findLifecycleEventsByTenantId("tenant-b").isEmpty(),
                        "the rejected replacement tenant must not receive lifecycle evidence"
                );
            } finally {
                releaseRetry.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }
        });
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

    private static final class ObservingConversionWorker implements ConversionWorker {
        private final InMemoryConversionJobRepository repository;
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<ConversionJob> lastJob = new AtomicReference<>();

        private ObservingConversionWorker(InMemoryConversionJobRepository repository) {
            this.repository = repository;
        }

        @Override
        public void enqueue(UUID jobId) {
            lastJob.set(repository.findById(jobId).orElse(null));
            count.incrementAndGet();
        }

        private int enqueuedCount() {
            return count.get();
        }

        private ConversionJob lastEnqueuedJob() {
            return lastJob.get();
        }
    }

    private static final class BlockingRetryStateStore implements ConversionJobStateStore {
        private final InMemoryConversionJobRepository delegate;
        private final CountDownLatch retryTransitioned;
        private final CountDownLatch releaseRetry;

        private BlockingRetryStateStore(
                InMemoryConversionJobRepository delegate,
                CountDownLatch retryTransitioned,
                CountDownLatch releaseRetry
        ) {
            this.delegate = delegate;
            this.retryTransitioned = retryTransitioned;
            this.releaseRetry = releaseRetry;
        }

        @Override
        public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
            return delegate.claimForProcessing(jobId, now);
        }

        @Override
        public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
            delegate.scheduleRetry(jobId, message, retryAt);
        }

        @Override
        public void markSucceeded(UUID jobId, String resourcePath, String message) {
            delegate.markSucceeded(jobId, resourcePath, message);
        }

        @Override
        public void markDeadLettered(UUID jobId, String message) {
            delegate.markDeadLettered(jobId, message);
        }

        @Override
        public boolean retryDeadLettered(UUID jobId, String operatorId) {
            return delegate.retryDeadLettered(jobId, operatorId);
        }

        @Override
        public TenantRetryOutcome retryDeadLetteredForTenant(
                String tenantId,
                UUID jobId,
                String operatorId
        ) {
            TenantRetryOutcome outcome = delegate.retryDeadLetteredForTenant(
                    tenantId,
                    jobId,
                    operatorId
            );
            if (outcome == TenantRetryOutcome.ACCEPTED) {
                retryTransitioned.countDown();
                awaitRelease();
            }
            return outcome;
        }

        private void awaitRelease() {
            try {
                if (!releaseRetry.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("retry release was not signalled");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("retry boundary was interrupted", exception);
            }
        }
    }
}
