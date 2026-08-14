package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that tenant-scoped identifier lookup never falls back to a global
 * repository read before tenant ownership is applied.
 */
class TenantScopedJobLookupBoundaryTest {

    @Test
    void repositoryDefaultTenantIdQueryFailsClosedWithoutGlobalLookup() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class, CALLS_REAL_METHODS);
        UUID jobId = UUID.randomUUID();

        assertTrue(repository.findByTenantAndId("tenant-a", jobId).isEmpty());

        verify(repository, never()).findById(jobId);
    }

    @Test
    void inMemoryTenantIdQueryUsesScopedStorageBoundary() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "operator-a",
                "tenant-a.docx",
                "application/octet-stream",
                "hash-a",
                42L,
                3
        );
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository() {
            @Override
            public Optional<ConversionJob> findById(UUID ignoredJobId) {
                throw new AssertionError("scoped lookup must not invoke global findById");
            }
        };
        repository.save(job);

        assertTrue(repository.findByTenantAndId(null, jobId).isEmpty());
        assertTrue(repository.findByTenantAndId(" ", jobId).isEmpty());
        assertTrue(repository.findByTenantAndId("tenant-a", null).isEmpty());
        assertTrue(repository.findByTenantAndId("tenant-b", jobId).isEmpty());
        assertSame(job, repository.findByTenantAndId(" tenant-a ", jobId).orElseThrow());
    }
}
