package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionCoordinator;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that durable deletion is isolated in a primary service decorator and
 * every non-deletion operation preserves the existing conversion-service contract.
 */
class DurableDocumentDeletionServiceTest {

    @Test
    void constructorRejectsMissingCollaborators() {
        DefaultDocumentConversionService delegate = mock(DefaultDocumentConversionService.class);
        ArtifactDeletionCoordinator coordinator = mock(ArtifactDeletionCoordinator.class);

        assertThrows(
                NullPointerException.class,
                () -> new DurableDocumentDeletionService(null, coordinator)
        );
        assertThrows(
                NullPointerException.class,
                () -> new DurableDocumentDeletionService(delegate, null)
        );
    }

    @Test
    void nonDeletionOperationsDelegateWithoutChangingArgumentsOrResults() {
        DefaultDocumentConversionService delegate = mock(DefaultDocumentConversionService.class);
        ArtifactDeletionCoordinator coordinator = mock(ArtifactDeletionCoordinator.class);
        DurableDocumentDeletionService service = new DurableDocumentDeletionService(
                delegate,
                coordinator
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                new byte[] {1, 2, 3}
        );
        PolicyOverrideRequest overrideRequest = PolicyOverrideRequest.none();
        TenantContext tenantContext = new TenantContext("tenant-a", "subject-a", Set.of());
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        ConversionJob job = mock(ConversionJob.class);
        List<ConversionJob> tenantJobs = List.of(job);
        List<ConversionJob> allJobs = List.of(job, mock(ConversionJob.class));
        when(delegate.submit(file)).thenReturn(firstId);
        when(delegate.submit(file, overrideRequest)).thenReturn(secondId);
        when(delegate.submit(file, overrideRequest, tenantContext)).thenReturn(thirdId);
        when(delegate.getJob(firstId)).thenReturn(Optional.of(job));
        when(delegate.retryDeadLettered(firstId, "operator")).thenReturn(RetryDeadLetterResult.ACCEPTED);
        when(delegate.retryDeadLettered(firstId, tenantContext, "operator"))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);
        when(delegate.getJobsForTenant(tenantContext)).thenReturn(tenantJobs);
        when(delegate.getAllJobs()).thenReturn(allJobs);

        assertEquals(firstId, service.submit(file));
        assertEquals(secondId, service.submit(file, overrideRequest));
        assertEquals(thirdId, service.submit(file, overrideRequest, tenantContext));
        assertSame(job, service.getJob(firstId).orElseThrow());
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(firstId, "operator")
        );
        assertEquals(
                RetryDeadLetterResult.NOT_ELIGIBLE,
                service.retryDeadLettered(firstId, tenantContext, "operator")
        );
        assertSame(tenantJobs, service.getJobsForTenant(tenantContext));
        assertSame(allJobs, service.getAllJobs());
        verify(coordinator, never()).deleteGlobally(firstId);
    }

    @Test
    void tenantDeletionFailsClosedWithoutContextAndOtherwiseUsesCoordinator() {
        DefaultDocumentConversionService delegate = mock(DefaultDocumentConversionService.class);
        ArtifactDeletionCoordinator coordinator = mock(ArtifactDeletionCoordinator.class);
        DurableDocumentDeletionService service = new DurableDocumentDeletionService(
                delegate,
                coordinator
        );
        UUID jobId = UUID.randomUUID();
        TenantContext tenantContext = new TenantContext("tenant-a", "subject-a", Set.of());
        when(coordinator.deleteForTenant(jobId, "tenant-a")).thenReturn(true);

        assertFalse(service.deleteJob(jobId, null));
        assertTrue(service.deleteJob(jobId, tenantContext));

        verify(coordinator).deleteForTenant(jobId, "tenant-a");
        verify(delegate, never()).deleteJob(jobId, tenantContext);
    }

    @Test
    void globalCompatibilityDeletionUsesCoordinator() {
        DefaultDocumentConversionService delegate = mock(DefaultDocumentConversionService.class);
        ArtifactDeletionCoordinator coordinator = mock(ArtifactDeletionCoordinator.class);
        DurableDocumentDeletionService service = new DurableDocumentDeletionService(
                delegate,
                coordinator
        );
        UUID jobId = UUID.randomUUID();

        service.deleteJob(jobId);

        verify(coordinator).deleteGlobally(jobId);
        verify(delegate, never()).deleteJob(jobId);
    }
}
