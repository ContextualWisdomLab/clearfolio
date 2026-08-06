package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that tenant-scoped content-index lookups never fall back to the demo
 * tenant when the authenticated tenant identifier is absent.
 */
class InMemoryConversionJobRepositoryFailClosedTenantLookupTest {

    @Test
    void nullOrBlankTenantCannotResolveTheDemoTenantContentIndex() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob demoJob = new ConversionJob(
                UUID.randomUUID(),
                "buyer-demo",
                "owner",
                "demo.pdf",
                "application/pdf",
                "demo-content-hash",
                100L,
                3
        );
        repository.save(demoJob);

        assertTrue(repository.findByTenantAndContentHash(null, demoJob.getContentHash()).isEmpty());
        assertTrue(repository.findByTenantAndContentHash("   ", demoJob.getContentHash()).isEmpty());
    }
}
