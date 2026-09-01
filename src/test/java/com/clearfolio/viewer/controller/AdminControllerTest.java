package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import org.springframework.http.HttpHeaders;
import java.util.Optional;

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
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "tenant-2", "user-2", "c.pdf", "application/pdf", "hash-c", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3));

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
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "user-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_DELETE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(context);
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "user-1", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "user-1", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        String expectedHash = "b32817bf034f5dcb3ac5f1e8dc3a19fc82dd24409bb10bc1a1f0a2dbb059f131"; // hash of user-1
        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "user-1", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        String expectedHash = "b32817bf034f5dcb3ac5f1e8dc3a19fc82dd24409bb10bc1a1f0a2dbb059f131";
        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = new TenantContext("tenant-1", "user-1", java.util.Set.of(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "user-1", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        String expectedHash = "b32817bf034f5dcb3ac5f1e8dc3a19fc82dd24409bb10bc1a1f0a2dbb059f131";
        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
