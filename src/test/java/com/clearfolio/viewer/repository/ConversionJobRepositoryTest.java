package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

class ConversionJobRepositoryTest {

    @Test
    void defaultTenantScopedOperationsFailClosedWithoutGlobalPaths() {
        AtomicInteger globalIdentifierLookupCalls = new AtomicInteger();
        AtomicInteger globalContentLookupCalls = new AtomicInteger();
        AtomicInteger globalListCalls = new AtomicInteger();
        AtomicInteger globalDeleteCalls = new AtomicInteger();
        ConversionJobRepository repository = new ConversionJobRepository() {
            @Override
            public ConversionJob save(ConversionJob job) {
                return job;
            }

            @Override
            public Optional<ConversionJob> findById(UUID jobId) {
                globalIdentifierLookupCalls.incrementAndGet();
                throw new AssertionError("tenant-scoped lookup used the global identifier path");
            }

            @Override
            public Optional<ConversionJob> findByContentHash(String contentHash) {
                globalContentLookupCalls.incrementAndGet();
                throw new AssertionError("tenant-scoped lookup used the global content-hash path");
            }

            @Override
            public FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
                return new FindOrStoreResult(candidate, true);
            }

            @Override
            public List<ConversionJob> findAll() {
                globalListCalls.incrementAndGet();
                throw new AssertionError("tenant-scoped listing used the global list path");
            }

            @Override
            public void deleteById(UUID jobId) {
                globalDeleteCalls.incrementAndGet();
                throw new AssertionError("tenant-scoped deletion used the global delete path");
            }
        };

        UUID jobId = UUID.randomUUID();
        assertTrue(repository.findByTenantAndId("tenant-a", jobId).isEmpty());
        assertTrue(repository.findByTenantAndId(null, jobId).isEmpty());
        assertTrue(repository.findByTenantAndId(" ", jobId).isEmpty());
        assertTrue(repository.findByTenantAndId("tenant-a", null).isEmpty());
        assertTrue(repository.findByTenantAndContentHash("tenant-a", "hash").isEmpty());
        assertTrue(repository.findByTenantAndContentHash(null, "hash").isEmpty());
        assertTrue(repository.findByTenantAndContentHash(" ", "hash").isEmpty());
        assertTrue(repository.findByTenantAndContentHash("tenant-a", null).isEmpty());
        assertTrue(repository.findAllByTenantId("tenant-a").isEmpty());
        assertTrue(repository.findAllByTenantId(null).isEmpty());
        assertFalse(repository.deleteByTenantAndId("tenant-a", jobId));
        assertFalse(repository.deleteByTenantAndId(null, jobId));
        assertEquals(0, globalIdentifierLookupCalls.get());
        assertEquals(0, globalContentLookupCalls.get());
        assertEquals(0, globalListCalls.get());
        assertEquals(0, globalDeleteCalls.get());
    }

    @Test
    void defaultFindRecoverableJobsReturnsDueSubmittedAndStaleProcessingOnly() {
        Instant now = Instant.parse("2026-07-03T00:00:00Z");
        Instant staleBefore = Instant.parse("2026-07-02T23:55:00Z");
        ConversionJob dueSubmitted = newJob("due-submitted");
        ConversionJob futureRetry = newJob("future-retry");
        futureRetry.markRetryScheduled("retry later", now.plusSeconds(30));
        ConversionJob staleProcessing = processingJob("stale-processing", staleBefore.minusSeconds(1));
        ConversionJob freshProcessing = processingJob("fresh-processing", staleBefore.plusSeconds(1));
        ConversionJob processingWithoutStart = processingJob("processing-no-start", null);
        ConversionJob exhaustedProcessing = exhaustedProcessingJob(
                "exhausted-processing",
                staleBefore.minusSeconds(1)
        );
        ConversionJob succeeded = newJob("succeeded");
        succeeded.markSucceeded("/artifacts/succeeded.pdf", "done");
        ConversionJob failed = newJob("failed");
        failed.markDeadLettered("dead");
        ConversionJobRepository repository = repositoryWith(
                dueSubmitted,
                futureRetry,
                staleProcessing,
                freshProcessing,
                processingWithoutStart,
                exhaustedProcessing,
                succeeded,
                failed
        );

        List<ConversionJob> recoverable = repository.findRecoverableJobs(now, staleBefore);

        assertEquals(List.of(dueSubmitted, staleProcessing), recoverable);
    }

    @Test
    void defaultFindRecoverableJobsRequiresRecoveryInstants() {
        ConversionJobRepository repository = repositoryWith(newJob("due-submitted"));
        Instant now = Instant.parse("2026-07-03T00:00:00Z");

        assertThrows(NullPointerException.class, () -> repository.findRecoverableJobs(null, now));
        assertThrows(NullPointerException.class, () -> repository.findRecoverableJobs(now, null));
    }

    private ConversionJobRepository repositoryWith(ConversionJob... jobs) {
        return new ConversionJobRepository() {
            @Override
            public ConversionJob save(ConversionJob job) {
                return job;
            }

            @Override
            public Optional<ConversionJob> findById(UUID jobId) {
                return Optional.empty();
            }

            @Override
            public Optional<ConversionJob> findByContentHash(String contentHash) {
                return Optional.empty();
            }

            @Override
            public FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
                return new FindOrStoreResult(candidate, true);
            }

            @Override
            public List<ConversionJob> findAll() {
                return List.of(jobs);
            }

            @Override
            public void deleteById(UUID jobId) {
            }
        };
    }

    private ConversionJob newJob(String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-a",
                "report.docx",
                "application/octet-stream",
                contentHash,
                10L,
                3
        );
    }

    private ConversionJob processingJob(String contentHash, Instant startedAt) {
        return new ProcessingJob(contentHash, startedAt, 3);
    }

    private ConversionJob exhaustedProcessingJob(String contentHash, Instant startedAt) {
        return new ProcessingJob(contentHash, startedAt, 1);
    }

    private final class ProcessingJob extends ConversionJob {
        private final Instant startedAt;

        private ProcessingJob(String contentHash, Instant startedAt, int maxAttempts) {
            super(
                    UUID.randomUUID(),
                    "tenant-a",
                    "subject-a",
                    "report.docx",
                    "application/octet-stream",
                    contentHash,
                    10L,
                    maxAttempts
            );
            assertTrue(markProcessing("started"));
            this.startedAt = startedAt;
        }

        @Override
        public Instant getStartedAt() {
            return startedAt;
        }

        @Override
        public ConversionJobStatus getStatus() {
            return ConversionJobStatus.PROCESSING;
        }
    }
}
