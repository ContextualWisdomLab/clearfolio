package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that lifecycle-event queries never infer the demo tenant from a
 * missing scoped tenant identifier.
 */
class InMemoryConversionJobRepositoryTenantLifecycleIsolationTest {

    @Test
    void nullAndBlankTenantQueriesCannotReadDemoLifecycleEvents() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob demoJob = new ConversionJob(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "buyer-demo",
                "subject-demo",
                "report.pdf",
                "application/pdf",
                "tenant-lifecycle-isolation-hash",
                42L,
                3
        );
        repository.findOrStoreByContentHash(demoJob);

        assertEquals(1, repository.findLifecycleEventsByTenantId("buyer-demo").size());
        assertTrue(repository.findLifecycleEventsByTenantId(null).isEmpty());
        assertTrue(repository.findLifecycleEventsByTenantId("   ").isEmpty());
    }
}
