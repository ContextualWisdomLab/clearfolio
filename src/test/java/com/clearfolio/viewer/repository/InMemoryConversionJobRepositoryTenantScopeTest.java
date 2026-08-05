package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Proves that administrative listing can be bounded at the repository layer
 * before another tenant's job objects reach the service or controller.
 */
class InMemoryConversionJobRepositoryTenantScopeTest {

    @Test
    void returnsOnlyJobsOwnedByTheRequestedTenant() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob northFirst = job("tenant-north", "north-first.pdf");
        ConversionJob northSecond = job("tenant-north", "north-second.pdf");
        ConversionJob southSecret = job("tenant-south", "south-secret.pdf");
        repository.save(northFirst);
        repository.save(southSecret);
        repository.save(northSecond);

        List<ConversionJob> tenantJobs = repository.findAllByTenantId("tenant-north");

        assertEquals(2, tenantJobs.size());
        assertEquals(
                Set.of(northFirst.getJobId(), northSecond.getJobId()),
                tenantJobs.stream().map(ConversionJob::getJobId).collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(tenantJobs.stream().noneMatch(job -> "tenant-south".equals(job.getTenantId())));
    }

    @Test
    void nullOrBlankTenantIdentifiersFailClosed() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(job("tenant-north", "north.pdf"));

        assertTrue(repository.findAllByTenantId(null).isEmpty());
        assertTrue(repository.findAllByTenantId("   ").isEmpty());
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "owner",
                fileName,
                "application/pdf",
                UUID.randomUUID().toString(),
                100L,
                3
        );
    }
}
