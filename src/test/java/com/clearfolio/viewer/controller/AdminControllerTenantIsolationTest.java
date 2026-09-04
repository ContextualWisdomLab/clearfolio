package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * Verifies that admin permissions do not bypass tenant resource isolation.
 */
class AdminControllerTenantIsolationTest {

    private static final TenantContext TENANT_A_ADMIN = new TenantContext(
            "tenant-a",
            "admin-a",
            Set.of(TenantPermissions.ADMIN_READ, TenantPermissions.ADMIN_WRITE));

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;

    /**
     * Builds the controller with a tenant-scoped administrative context.
     */
    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        when(tenantAccessService.require(any(HttpHeaders.class), any(String.class)))
                .thenReturn(TENANT_A_ADMIN);

        AdminController controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /**
     * A tenant-scoped administrator must not enumerate another tenant's jobs.
     */
    @Test
    void listJobsExcludesForeignTenantJobs() {
        ConversionJob owned = jobFor("tenant-a", "owned.pdf");
        ConversionJob foreign = jobFor("tenant-b", "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(List.of(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Test", "tenant-isolation")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    /**
     * A tenant-scoped administrator must not delete another tenant's job.
     */
    @Test
    void deleteJobHidesForeignTenantResource() {
        ConversionJob foreign = jobFor("tenant-b", "foreign.pdf");
        UUID jobId = foreign.getJobId();
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(foreign));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService)
                .requireSameTenant(eq(TENANT_A_ADMIN), eq(foreign));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header("X-Test", "tenant-isolation")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    /**
     * A tenant-scoped administrator must not retry another tenant's job.
     */
    @Test
    void retryJobHidesForeignTenantResource() {
        ConversionJob foreign = jobFor("tenant-b", "foreign.pdf");
        UUID jobId = foreign.getJobId();
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(foreign));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService)
                .requireSameTenant(eq(TENANT_A_ADMIN), eq(foreign));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Test", "tenant-isolation")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(eq(jobId), any(String.class));
    }

    private static ConversionJob jobFor(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                "hash",
                100L,
                3);
    }
}
