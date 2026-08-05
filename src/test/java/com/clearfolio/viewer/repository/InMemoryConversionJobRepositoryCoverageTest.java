package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies deletion behavior for missing jobs and jobs without an indexable
 * content hash.
 */
class InMemoryConversionJobRepositoryCoverageTest {

    @Test
    void deleteIsIdempotentForMissingJobsAndRemovesJobsWithNullOrBlankHashes() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();

        repository.deleteById(UUID.randomUUID());

        ConversionJob nullHashJob = job(null);
        repository.save(nullHashJob);
        repository.deleteById(nullHashJob.getJobId());
        assertTrue(repository.findById(nullHashJob.getJobId()).isEmpty());

        ConversionJob blankHashJob = job(" ");
        repository.save(blankHashJob);
        repository.deleteById(blankHashJob.getJobId());
        assertTrue(repository.findById(blankHashJob.getJobId()).isEmpty());
    }

    private static ConversionJob job(String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-a",
                "document.pdf",
                "application/pdf",
                contentHash,
                1L,
                3
        );
    }
}
