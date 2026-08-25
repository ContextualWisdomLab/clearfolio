package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(ctx);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob jobOtherTenant = new ConversionJob(UUID.randomUUID(), "other-tenant", "admin", "c.pdf", "application/pdf", "hash-c", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, jobOtherTenant));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(ctx);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(ctx);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "admin-tenant", "admin", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobDelegatesTenantScopedDelete() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_DELETE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(ctx);
        when(conversionService.deleteJob(jobId, ctx)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId, ctx);
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsNotFoundWhenTenantScopedDeleteRejectsJob() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_DELETE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(ctx);
        when(conversionService.deleteJob(jobId, ctx)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(jobId, ctx);
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(ctx);
        ConversionJob job = new ConversionJob(jobId, "admin-tenant", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredWithEmptyOptional() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(ctx);
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(ctx);
        ConversionJob job = new ConversionJob(jobId, "admin-tenant", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("admin-tenant", "admin", Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(ctx);
        ConversionJob job = new ConversionJob(jobId, "admin-tenant", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
