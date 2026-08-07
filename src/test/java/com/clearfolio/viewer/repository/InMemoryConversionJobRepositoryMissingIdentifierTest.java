package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobStateStore.TenantRetryOutcome;

/**
 * Proves that tenant-scoped repository operations select only owned jobs and
 * conceal missing or cross-tenant identifiers.
 */
class InMemoryConversionJobRepositoryMissingIdentifierTest {

    /**
     * Verifies that the concrete adapter performs an owned identifier lookup
     * while concealing the same identifier from every other tenant.
     */
    @Test
    void tenantIdentifierLookupReturnsOnlyTheOwnedJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob ownedJob = ownedJob();
        repository.save(ownedJob);

        assertSame(
                ownedJob,
                repository.findByTenantAndId(" tenant-north ", ownedJob.getJobId()).orElseThrow()
        );
        assertTrue(repository.findByTenantAndId("tenant-south", ownedJob.getJobId()).isEmpty());
        assertTrue(repository.findByTenantAndId(null, ownedJob.getJobId()).isEmpty());
        assertTrue(repository.findByTenantAndId(" ", ownedJob.getJobId()).isEmpty());
        assertTrue(repository.findById(ownedJob.getJobId()).isPresent());
    }

    /**
     * Exercises each tenant-scoped lookup or mutation with a missing job
     * identifier while a real tenant-owned job exists. The existing job proves
     * the result is caused by the missing identifier rather than an empty store.
     */
    @Test
    void missingJobIdentifierFailsClosedAcrossScopedOperations() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob ownedJob = ownedJob();
        repository.save(ownedJob);

        assertTrue(repository.findByTenantAndId("tenant-north", null).isEmpty());
        assertFalse(repository.deleteByTenantAndId("tenant-north", null));
        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant("tenant-north", null, "actor:v1")
        );
        assertTrue(repository.findById(ownedJob.getJobId()).isPresent());
    }

    private static ConversionJob ownedJob() {
        return new ConversionJob(
                UUID.randomUUID(),
                "tenant-north",
                "subject-north",
                "contract.pdf",
                "application/pdf",
                "content-hash",
                128L,
                3
        );
    }
}
