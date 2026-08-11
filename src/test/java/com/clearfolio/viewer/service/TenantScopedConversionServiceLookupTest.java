package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Proves that application-service lookup can preserve tenant authority all the
 * way to the repository lookup boundary instead of reading global job state
 * first and filtering ownership afterward.
 */
class TenantScopedConversionServiceLookupTest {

    @Test
    void interfaceDefaultFailsClosedWithoutGlobalLookup() {
        DocumentConversionService service = mock(DocumentConversionService.class, CALLS_REAL_METHODS);
        UUID jobId = UUID.randomUUID();
        TenantContext tenant = new TenantContext("tenant-a", "subject-a", Set.of());

        assertTrue(service.getJob(jobId, tenant).isEmpty());

        verify(service, never()).getJob(jobId);
    }

    @Test
    void defaultServiceDelegatesTenantPredicateToRepository() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "subject-a",
                "report.pdf",
                "application/pdf",
                "hash-a",
                42L,
                3
        );
        TenantContext tenant = new TenantContext("tenant-a", "subject-a", Set.of());
        when(repository.findByTenantAndId("tenant-a", jobId)).thenReturn(Optional.of(job));
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> { },
                ignoredJobId -> { },
                new ConversionProperties()
        );

        assertSame(job, service.getJob(jobId, tenant).orElseThrow());
        assertTrue(service.getJob(jobId, null).isEmpty());
        assertTrue(service.getJob(null, tenant).isEmpty());

        verify(repository).findByTenantAndId("tenant-a", jobId);
        verify(repository, never()).findById(jobId);
    }
}
