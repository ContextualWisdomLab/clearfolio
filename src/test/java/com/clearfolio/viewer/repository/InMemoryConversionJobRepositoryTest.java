package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

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
