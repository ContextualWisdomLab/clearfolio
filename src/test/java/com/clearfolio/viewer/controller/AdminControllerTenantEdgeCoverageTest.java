package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTenantEdgeCoverageTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private AdminController controller;
    private final HttpHeaders headers = new HttpHeaders();
    private final TenantContext tenantContext = new TenantContext(
            "tenant-a",
            "subject-a",
            Set.of(
                    TenantPermissions.JOB_READ,
                    TenantPermissions.JOB_DELETE,
                    TenantPermissions.JOB_RETRY));

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        when(tenantAccessService.require(any(HttpHeaders.class), any()))
                .thenReturn(tenantContext);
        controller = new AdminController(conversionService, tenantAccessService);
    }

    @Test
    void listOmitsJobsOwnedByAnotherTenant() {
        ConversionJob ownJob = job("tenant-a");
        ConversionJob foreignJob = job("tenant-b");
        when(conversionService.getAllJobs()).thenReturn(List.of(foreignJob, ownJob));

        AdminJobListResponse response = controller.getAllJobs(null, headers);

        assertEquals(1, response.jobs().size());
        assertEquals(ownJob.getJobId(), response.jobs().get(0).jobId());
    }

    @Test
    void deleteReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE)))
                .thenReturn(tenantContext);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.deleteJob(jobId, headers));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void retryReturnsNotFoundWhenJobDisappearsAfterAuthorization() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "subject-a",
                "a.pdf",
                "application/pdf",
                "hash-a",
                100L,
                3);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenReturn(tenantContext);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject-a"))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.retryDeadLettered(jobId, headers));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private ConversionJob job(final String tenantId) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                tenantId + ".pdf",
                "application/pdf",
                "hash-" + tenantId,
                100L,
                3);
    }
}
