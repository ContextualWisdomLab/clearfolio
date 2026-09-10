package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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

        TenantContext context = new TenantContext("tenant", "subject", Set.of(
                TenantPermissions.JOB_READ,
                TenantPermissions.JOB_DELETE,
                TenantPermissions.JOB_RETRY
        ));
        when(tenantAccessService.require(any(HttpHeaders.class), any())).thenReturn(context);

        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
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
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant", "subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant", "subject", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void getAllJobsRequiresPermission() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobRequiresPermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredRequiresPermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();
    }
}
