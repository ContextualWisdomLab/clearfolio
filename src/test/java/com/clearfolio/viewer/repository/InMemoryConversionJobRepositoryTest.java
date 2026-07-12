package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

class InMemoryConversionJobRepositoryTest {

    @Test
    void storesAndFindsJobByIdAndContentHash() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-a");

        repository.save(job);

        assertTrue(repository.findById(job.getJobId()).isPresent());
        assertTrue(repository.findByContentHash("hash-a").isPresent());
    }

    @Test
    void findByTenantAndContentHashFallsBackToDemoTenantWhenTenantIsNull() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-default-tenant");
        repository.save(job);

        assertSame(job, repository.findByTenantAndContentHash(null, "hash-default-tenant").orElseThrow());
    }

    @Test
    void findByTenantAndContentHashFallsBackToDemoTenantWhenTenantIsBlank() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-blank-tenant");
        repository.save(job);

        assertSame(job, repository.findByTenantAndContentHash(" ", "hash-blank-tenant").orElseThrow());
    }

    @Test
    void findAllReturnsStoredJobsSnapshot() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob first = newJob("hash-a");
        ConversionJob second = newJob("hash-b");
        repository.save(first);
        repository.save(second);

        assertTrue(repository.findAll().contains(first));
        assertTrue(repository.findAll().contains(second));
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void findByContentHashReturnsEmptyForBlankOrMissingHash() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();

        assertTrue(repository.findByContentHash(null).isEmpty());
        assertTrue(repository.findByContentHash(" ").isEmpty());
        assertTrue(repository.findByContentHash("missing").isEmpty());
    }

    @Test
    void findByContentHashReturnsEmptyWhenHashIndexPointsToMissingJob() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob original = newJob("hash-b");
        repository.save(original);

        jobs(repository).remove(original.getJobId());

        assertTrue(repository.findByContentHash("hash-b").isEmpty());
    }

    @Test
    void findOrStoreReturnsExistingJobForDuplicateHash() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob first = newJob("hash-c");
        ConversionJob second = newJob("hash-c");
        repository.save(first);

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(second);

        assertSame(first, result.canonicalJob());
        assertFalse(result.created());
        assertFalse(repository.findById(second.getJobId()).isPresent());
    }

    @Test
    void findOrStoreCreatesSeparateJobForSameHashInDifferentTenant() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob first = newJob("hash-shared");
        ConversionJob second = newTenantJob("tenant-b", "hash-shared");
        repository.save(first);

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(second);

        assertSame(second, result.canonicalJob());
        assertTrue(result.created());
        assertTrue(repository.findById(first.getJobId()).isPresent());
        assertTrue(repository.findById(second.getJobId()).isPresent());
        assertSame(first, repository.findByContentHash("hash-shared").orElseThrow());
        assertSame(second, repository.findByTenantAndContentHash("tenant-b", "hash-shared").orElseThrow());
    }

    @Test
    void findOrStoreMarksCreatedWhenHashIsStoredFirstTime() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob candidate = newJob("hash-created");

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(candidate);

        assertSame(candidate, result.canonicalJob());
        assertTrue(result.created());
    }

    @Test
    void findOrStoreFallsBackToCandidateWhenIndexedWinnerIsMissing() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob original = newJob("hash-d");
        repository.save(original);
        jobs(repository).remove(original.getJobId());

        ConversionJob candidate = newJob("hash-d");

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(candidate);

        assertSame(candidate, result.canonicalJob());
        assertTrue(result.created());
        assertEquals(candidate.getJobId(), jobsByContentHash(repository).get("buyer-demo\u001fhash-d"));
        assertTrue(repository.findById(candidate.getJobId()).isPresent());
    }

    @Test
    void findOrStoreSavesCandidateWhenHashIsBlank() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob candidate = newJob(" ");

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(candidate);

        assertSame(candidate, result.canonicalJob());
        assertTrue(result.created());
        assertTrue(repository.findById(candidate.getJobId()).isPresent());
    }

    @Test
    void saveDoesNotIndexNullContentHash() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob(null);

        repository.save(job);

        assertTrue(repository.findById(job.getJobId()).isPresent());
        assertTrue(jobsByContentHash(repository).isEmpty());
    }

    @Test
    void saveDoesNotIndexBlankContentHash() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("   ");

        repository.save(job);

        assertTrue(repository.findById(job.getJobId()).isPresent());
        assertTrue(jobsByContentHash(repository).isEmpty());
    }

    @Test
    void findOrStoreSavesCandidateWhenHashIsNull() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob candidate = newJob(null);

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(candidate);

        assertSame(candidate, result.canonicalJob());
        assertTrue(result.created());
        assertTrue(repository.findById(candidate.getJobId()).isPresent());
        assertTrue(jobsByContentHash(repository).isEmpty());
    }

    @Test
    void findOrStoreRecordsSubmittedAndDedupeHitLifecycleEventsWithoutSourceMetadata() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob candidate = newJob("hash-event");
        ConversionJob duplicate = newJob("hash-event");

        ConversionJobRepository.FindOrStoreResult created = repository.findOrStoreByContentHash(candidate);
        ConversionJobRepository.FindOrStoreResult reused = repository.findOrStoreByContentHash(duplicate);

        assertTrue(created.created());
        assertFalse(reused.created());
        assertSame(candidate, reused.canonicalJob());

        List<ConversionJobLifecycleEvent> events = repository.findLifecycleEventsByJobId(candidate.getJobId());
        assertIterableEquals(
                List.of("conversion.job.submitted", "conversion.job.dedupe_hit"),
                events.stream().map(ConversionJobLifecycleEvent::eventType).toList()
        );
        assertEquals(ConversionJobStatus.SUBMITTED, events.getFirst().statusAfter());
        assertEquals(0, events.getFirst().attemptCount());
        assertEquals(2, repository.findLifecycleEventsByTenantId("buyer-demo").size());
        assertFalse(events.toString().contains("report.docx"));
        assertFalse(events.toString().contains("hash-event"));
    }

    @Test
    void stateStoreRecordsTransitionLifecycleEventsInOrder() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-transition-events");
        repository.findOrStoreByContentHash(job);
        ConversionJobStateStore stateStore = repository;
        Instant retryAt = Instant.now().minusMillis(1);

        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isPresent());
        stateStore.scheduleRetry(job.getJobId(), "retry later", retryAt);
        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isPresent());
        stateStore.markSucceeded(job.getJobId(), "/artifacts/" + job.getJobId() + ".pdf", "done");

        List<ConversionJobLifecycleEvent> events = repository.findLifecycleEventsByJobId(job.getJobId());
        assertIterableEquals(
                List.of(
                        "conversion.job.submitted",
                        "conversion.processing.started",
                        "conversion.retry.scheduled",
                        "conversion.processing.started",
                        "conversion.job.succeeded"
                ),
                events.stream().map(ConversionJobLifecycleEvent::eventType).toList()
        );
        assertEquals(ConversionJobStatus.PROCESSING, events.get(1).statusAfter());
        assertEquals(1, events.get(1).attemptCount());
        assertEquals(ConversionJobStatus.PROCESSING, events.get(2).statusBefore());
        assertEquals(ConversionJobStatus.SUBMITTED, events.get(2).statusAfter());
        assertEquals(retryAt, events.get(2).retryAt());
        assertEquals(2, events.get(3).attemptCount());
        assertEquals(ConversionJobStatus.SUCCEEDED, events.get(4).statusAfter());
    }

    @Test
    void retryDeadLetteredRecordsAcceptedEventOnlyWhenTransitionSucceeds() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-retry-accepted-event");
        repository.findOrStoreByContentHash(job);
        ConversionJobStateStore stateStore = repository;

        assertTrue(stateStore.claimForProcessing(job.getJobId(), Instant.now()).isPresent());
        stateStore.markDeadLettered(job.getJobId(), "retries exhausted");
        assertTrue(stateStore.retryDeadLettered(job.getJobId(), "operator-7"));
        assertFalse(stateStore.retryDeadLettered(job.getJobId(), "operator-7"));

        List<ConversionJobLifecycleEvent> events = repository.findLifecycleEventsByJobId(job.getJobId());
        assertIterableEquals(
                List.of(
                        "conversion.job.submitted",
                        "conversion.processing.started",
                        "conversion.job.failed",
                        "conversion.retry.accepted"
                ),
                events.stream().map(ConversionJobLifecycleEvent::eventType).toList()
        );
        assertEquals(ConversionJobStatus.FAILED, events.get(2).statusAfter());
        assertEquals(ConversionJobStatus.SUBMITTED, events.get(3).statusAfter());
        assertEquals(0, events.get(3).attemptCount());
    }

    @Test
    void stateStoreClaimForProcessingReturnsEmptyForMissingOrBusyJobs() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob processing = newJob("hash-claim-busy");
        assertTrue(processing.markProcessing("started"));
        repository.save(processing);
        ConversionJobStateStore stateStore = repository;

        assertTrue(stateStore.claimForProcessing(UUID.randomUUID(), Instant.now()).isEmpty());
        assertTrue(stateStore.claimForProcessing(processing.getJobId(), Instant.now()).isEmpty());
        assertEquals(ConversionJobStatus.PROCESSING, processing.getStatus());
    }

    @Test
    void stateStoreRetryDeadLetteredReturnsFalseForMissingJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJobStateStore stateStore = repository;

        assertFalse(stateStore.retryDeadLettered(UUID.randomUUID(), "operator-1"));
    }

    @Test
    void stateStoreClaimForProcessingUpdatesStoredJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-claim");
        repository.save(job);
        ConversionJobStateStore stateStore = repository;

        Optional<ConversionJob> claimed = stateStore.claimForProcessing(job.getJobId(), Instant.now());

        assertTrue(claimed.isPresent());
        assertSame(job, claimed.orElseThrow());
        assertEquals(ConversionJobStatus.PROCESSING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertNull(job.getRetryAt());
    }

    @Test
    void stateStoreScheduleRetryPersistsSubmittedRetryTime() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-retry-state");
        assertTrue(job.markProcessing("started"));
        repository.save(job);
        ConversionJobStateStore stateStore = repository;
        Instant retryAt = Instant.now().plusSeconds(5);

        stateStore.scheduleRetry(job.getJobId(), "retry later", retryAt);

        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertSame(job, repository.findById(job.getJobId()).orElseThrow());
        assertEquals(retryAt, job.getRetryAt());
        assertEquals("retry later", job.getStatusMessage());
    }

    @Test
    void stateStoreRetryDeadLetteredResetsJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-retry-dead-letter");
        assertTrue(job.markProcessing("started"));
        job.markDeadLettered("retries exhausted");
        repository.save(job);
        ConversionJobStateStore stateStore = repository;

        assertTrue(stateStore.retryDeadLettered(job.getJobId(), "operator-7"));

        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertFalse(job.isDeadLettered());
        assertEquals(0, job.getAttemptCount());
        assertEquals("operator retry queued by operator-7", job.getStatusMessage());
    }

    @Test
    void stateStoreDeadLetterDoesNotDowngradeSucceededJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-succeeded-saturation");
        job.markSucceeded("/artifacts/" + job.getJobId() + ".pdf", "done");
        repository.save(job);
        ConversionJobStateStore stateStore = repository;

        stateStore.markDeadLettered(job.getJobId(), "worker queue saturated");

        assertEquals(ConversionJobStatus.SUCCEEDED, job.getStatus());
        assertFalse(job.isDeadLettered());
        assertEquals("done", job.getStatusMessage());
    }

    @Test
    void findAllReturnsAllStoredJobs() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();

        Iterable<ConversionJob> initial = repository.findAll();
        assertFalse(initial.iterator().hasNext(), "findAll should be empty initially");

        ConversionJob job1 = newJob("hash-1");
        ConversionJob job2 = newJob("hash-2");

        repository.save(job1);
        repository.save(job2);

        Iterable<ConversionJob> all = repository.findAll();
        int count = 0;
        boolean found1 = false;
        boolean found2 = false;

        for (ConversionJob job : all) {
            count++;
            if (job.getJobId().equals(job1.getJobId())) {
                found1 = true;
            }
            if (job.getJobId().equals(job2.getJobId())) {
                found2 = true;
            }
        }

        assertEquals(2, count);
        assertTrue(found1);
        assertTrue(found2);
    }

    @Test
    void findAllReturnsImmutableSnapshot() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = newJob("hash-snapshot");
        ConversionJob laterJob = newJob("hash-later");
        repository.save(job);

        Iterable<ConversionJob> snapshot = repository.findAll();
        repository.save(laterJob);

        int count = 0;
        boolean foundOriginal = false;
        boolean foundLater = false;
        for (ConversionJob current : snapshot) {
            count++;
            foundOriginal = foundOriginal || current.getJobId().equals(job.getJobId());
            foundLater = foundLater || current.getJobId().equals(laterJob.getJobId());
        }

        assertEquals(1, count);
        assertTrue(foundOriginal);
        assertFalse(foundLater);
        assertThrows(UnsupportedOperationException.class, () -> ((Collection<?>) snapshot).clear());
        assertTrue(repository.findById(job.getJobId()).isPresent());
    }

    private ConversionJob newJob(String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                "report.docx",
                "application/octet-stream",
                contentHash,
                42L,
                3
        );
    }

    private ConversionJob newTenantJob(String tenantId, String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject-1",
                "report.docx",
                "application/octet-stream",
                contentHash,
                42L,
                3
        );
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, ConversionJob> jobs(InMemoryConversionJobRepository repository) throws Exception {
        Field jobsField = InMemoryConversionJobRepository.class.getDeclaredField("jobs");
        jobsField.setAccessible(true);
        return (ConcurrentHashMap<UUID, ConversionJob>) jobsField.get(repository);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, UUID> jobsByContentHash(InMemoryConversionJobRepository repository) throws Exception {
        Field hashField = InMemoryConversionJobRepository.class.getDeclaredField("jobsByTenantAndContentHash");
        hashField.setAccessible(true);
        return (ConcurrentHashMap<String, UUID>) hashField.get(repository);
    }
}
