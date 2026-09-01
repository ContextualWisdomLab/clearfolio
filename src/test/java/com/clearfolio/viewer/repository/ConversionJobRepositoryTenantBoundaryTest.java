package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Compatibility regression for the persistence-port tenant query used by the
 * application-service authorization boundary.
 */
class ConversionJobRepositoryTenantBoundaryTest {

    @Test
    void defaultTenantQueryFiltersOwnedRowsAndFailsClosedForNullTenant() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob tenantA = job("tenant-a", "a.pdf", "hash-a");
        ConversionJob tenantB = job("tenant-b", "b.pdf", "hash-b");
        repository.save(tenantA);
        repository.save(tenantB);

        assertEquals(List.of(tenantA), repository.findAllByTenant("tenant-a"));
        assertTrue(repository.findAllByTenant(null).isEmpty());
    }

    private static ConversionJob job(String tenantId, String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                hash,
                10L,
                3
        );
    }
}
