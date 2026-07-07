package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

class RepositoryBackedConversionJobStateStoreTest {

    @Test
    void claimForProcessingReturnsEmptyWhenJobMissing() {
        CountingRepository repository = new CountingRepository();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertTrue(stateStore.claimForProcessing(UUID.randomUUID(), Instant.now()).isEmpty());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void claimForProcessingReturnsEmptyWhenJobIsNotReady() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-not-ready");
        job.markRetryScheduled("later", Instant.now().plusSeconds(30));
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isEmpty());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void claimForProcessingSavesReadyJob() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-ready");
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isPresent());

        assertEquals(ConversionJobStatus.PROCESSING, job.getStatus());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void claimForProcessingReturnsEmptyWhenTransitionIsLostToRace() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "report.docx",
                "application/octet-stream",
                "hash-claim-race",
                12L,
                3
        ) {
            @Override
            public synchronized boolean isReadyForProcessing(Instant now) {
                return true;
            }

            @Override
            public synchronized boolean markProcessing(String message) {
                return false;
            }
        };
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isEmpty());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void markDeadLetteredUpdatesProcessingJob() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-processing-dead");
        assertTrue(job.markProcessing("started"));
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        stateStore.markDeadLettered(job.getJobId(), "dead");

        assertEquals(ConversionJobStatus.FAILED, job.getStatus());
        assertTrue(job.isDeadLettered());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void scheduleRetryMarksSubmittedAndSaves() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-retry");
        assertTrue(job.markProcessing("started"));
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);
        Instant retryAt = Instant.now().plusSeconds(5);

        stateStore.scheduleRetry(job.getJobId(), "retry", retryAt);

        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertEquals(retryAt, job.getRetryAt());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void markSucceededSavesCompletedJob() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-success");
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        stateStore.markSucceeded(job.getJobId(), "/artifacts/" + job.getJobId() + ".pdf", "done");

        assertEquals(ConversionJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void markDeadLetteredOnlyUpdatesActiveJobs() {
        CountingRepository repository = new CountingRepository();
        ConversionJob active = newJob("hash-active");
        ConversionJob succeeded = newJob("hash-succeeded");
        succeeded.markSucceeded("/artifacts/" + succeeded.getJobId() + ".pdf", "done");
        repository.save(active);
        repository.save(succeeded);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        stateStore.markDeadLettered(active.getJobId(), "dead");
        stateStore.markDeadLettered(succeeded.getJobId(), "queue saturated");

        assertEquals(ConversionJobStatus.FAILED, active.getStatus());
        assertTrue(active.isDeadLettered());
        assertEquals(ConversionJobStatus.SUCCEEDED, succeeded.getStatus());
        assertFalse(succeeded.isDeadLettered());
        assertEquals(1, repository.saveCount());
    }

    @Test
    void retryDeadLetteredReturnsFalseWhenMissingOrNotEligible() {
        CountingRepository repository = new CountingRepository();
        ConversionJob submitted = newJob("hash-submitted");
        repository.save(submitted);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertFalse(stateStore.retryDeadLettered(UUID.randomUUID(), "operator-1"));
        assertFalse(stateStore.retryDeadLettered(submitted.getJobId(), "operator-1"));
        assertEquals(0, repository.saveCount());
    }

    @Test
    void retryDeadLetteredSavesAcceptedRetry() {
        CountingRepository repository = new CountingRepository();
        ConversionJob job = newJob("hash-dead-letter");
        assertTrue(job.markProcessing("started"));
        job.markDeadLettered("dead");
        repository.save(job);
        repository.resetSaveCount();
        RepositoryBackedConversionJobStateStore stateStore = new RepositoryBackedConversionJobStateStore(repository);

        assertTrue(stateStore.retryDeadLettered(job.getJobId(), "operator-1"));

        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertEquals(1, repository.saveCount());
    }

    private ConversionJob newJob(String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                "report.docx",
                "application/octet-stream",
                contentHash,
                12L,
                3
        );
    }

    private static class CountingRepository extends InMemoryConversionJobRepository {
        private int saveCount;

        @Override
        public ConversionJob save(ConversionJob job) {
            saveCount++;
            return super.save(job);
        }

        int saveCount() {
            return saveCount;
        }

        void resetSaveCount() {
            saveCount = 0;
        }
    }
}
