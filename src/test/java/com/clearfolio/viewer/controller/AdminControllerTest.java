package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void mockTenantContext(String permission) {
        TenantContext context = new TenantContext("tenant-a", "user-1", null);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(permission)))
                .thenReturn(context);
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        mockTenantContext(TenantPermissions.JOB_READ);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "tenant-b", "user-1",
                "c.pdf", "application/pdf", "hash-c", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        mockTenantContext(TenantPermissions.JOB_READ);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        mockTenantContext(TenantPermissions.JOB_READ);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-a", "user-1",
                "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        mockTenantContext(TenantPermissions.JOB_DELETE);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        mockTenantContext(TenantPermissions.JOB_RETRY);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), anyString()))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        mockTenantContext(TenantPermissions.JOB_RETRY);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), anyString()))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        mockTenantContext(TenantPermissions.JOB_RETRY);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-a", "user-1",
                "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), anyString()))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-a")
                .header(TenantContext.SUBJECT_ID_HEADER, "user-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
