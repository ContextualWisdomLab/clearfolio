package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
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
    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();

        tenantContext = mock(TenantContext.class);
        when(tenantContext.tenantId()).thenReturn("tenant-1");
        when(tenantContext.subjectId()).thenReturn("subject-1");
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
    void getAllJobsHidesCrossTenantJobs() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-2", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3); // different tenant
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }


    @Test
    void deleteJobReturnsNoContent() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobReturnsNotFoundWhenJobDoesNotExist() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDoesNotExist() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void retryDeadLetteredThrowsWhenOperatorIdBlank() {
        TenantContext blankContext = mock(TenantContext.class);
        when(blankContext.tenantId()).thenReturn("tenant-1");
        when(blankContext.subjectId()).thenReturn("");

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(blankContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(500);
    }
}
