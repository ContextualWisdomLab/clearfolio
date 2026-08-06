package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Defines the service-layer boundary for tenant-scoped administrative job lists.
 */
class TenantScopedAdministrativeListContractTest {

    @Test
    void delegatesOnlyTheAuthenticatedTenantIdentifierToTheRepository() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJob tenantJob = job("tenant-north");
        when(repository.findAllByTenantId("tenant-north")).thenReturn(List.of(tenantJob));
        DefaultDocumentConversionService service = service(repository);
        TenantContext context = new TenantContext(
                "tenant-north",
                "administrator",
                Set.of("admin:read")
        );

        Iterable<ConversionJob> result = service.getJobsForTenant(context);
        List<ConversionJob> jobs = java.util.stream.StreamSupport
                .stream(result.spliterator(), false)
                .toList();

        assertEquals(1, jobs.size());
        assertSame(tenantJob, jobs.getFirst());
        verify(repository).findAllByTenantId("tenant-north");
        verify(repository, never()).findAll();
    }

    @Test
    void absentTenantContextFailsClosedBeforeRepositoryAccess() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        DefaultDocumentConversionService service = service(repository);

        Iterable<ConversionJob> result = service.getJobsForTenant(null);

        assertTrue(java.util.stream.StreamSupport.stream(result.spliterator(), false).findAny().isEmpty());
        verifyNoInteractions(repository);
    }

    private static DefaultDocumentConversionService service(ConversionJobRepository repository) {
        return new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                jobId -> {
                },
                new InMemoryArtifactStore(),
                new ConversionProperties()
        );
    }

    private static ConversionJob job(String tenantId) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "owner",
                "contract.pdf",
                "application/pdf",
                UUID.randomUUID().toString(),
                100L,
                3
        );
    }
}
