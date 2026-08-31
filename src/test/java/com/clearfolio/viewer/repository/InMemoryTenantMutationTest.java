package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * Regression tests for tenant-bound repository mutations.
 */
class InMemoryTenantMutationTest {

    @Test
    void deleteByTenantAndIdRejectsMissingAndCrossTenantTargets() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, "tenant-a", "hash-delete");
        repository.save(job);

        assertFalse(repository.deleteByTenantAndId("tenant-b", jobId));
        assertTrue(repository.findById(jobId).isPresent());
        assertFalse(repository.deleteByTenantAndId("tenant-a", UUID.randomUUID()));
        assertTrue(repository.deleteByTenantAndId("tenant-a", jobId));
        assertTrue(repository.findById(jobId).isEmpty());
        assertTrue(repository.findByTenantAndContentHash("tenant-a", "hash-delete").isEmpty());
    }

    @Test
    void retryDeadLetteredByTenantRejectsMissingCrossTenantAndIneligibleTargets() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, "tenant-a", "hash-retry");
        repository.save(job);

        assertFalse(repository.retryDeadLettered(UUID.randomUUID(), "tenant-a", "operator"));
        assertFalse(repository.retryDeadLettered(jobId, "tenant-b", "operator"));
        assertFalse(repository.retryDeadLettered(jobId, "tenant-a", "operator"));
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());

        repository.markDeadLettered(jobId, "failed");
        assertEquals(ConversionJobStatus.DEAD_LETTERED, job.getStatus());
        assertTrue(repository.retryDeadLettered(jobId, "tenant-a", "operator"));
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
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
