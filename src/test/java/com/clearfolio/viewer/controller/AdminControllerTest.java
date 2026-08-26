package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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

    private static final long SIZE = 100L;
    private static final int ATTEMPTS = 3;

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        final AdminController controller = new AdminController(
                conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsTenantJobsWhenNoFilterProvided() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenReturn(context);

        final ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        final ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant2", "sub", "b.pdf",
                "application/pdf", "hash-b", SIZE, ATTEMPTS);
        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrueForTenant() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenReturn(context);

        final ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        job1.markDeadLettered("failed");
        final ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant1", "sub", "b.pdf",
                "application/pdf", "hash-b", SIZE, ATTEMPTS);

        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalseForTenant() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_READ));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenReturn(context);

        final ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        job1.markDeadLettered("failed");
        final ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant1", "sub", "b.pdf",
                "application/pdf", "hash-b", SIZE, ATTEMPTS);

        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContentForTenantJob() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_DELETE));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE)))
                .thenReturn(context);

        final UUID jobId = UUID.randomUUID();
        final ConversionJob job = new ConversionJob(
                jobId, "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobReturnsNotFoundForOtherTenantJob() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_DELETE));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE)))
                .thenReturn(context);

        final UUID jobId = UUID.randomUUID();
        final ConversionJob job = new ConversionJob(
                jobId, "tenant2", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenReturn(context);

        final UUID jobId = UUID.randomUUID();
        final ConversionJob job = new ConversionJob(
                jobId, "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(
                eq(jobId), any(String.class)))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenReturn(context);

        final UUID jobId = UUID.randomUUID();
        final ConversionJob job = new ConversionJob(
                jobId, "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(
                eq(jobId), any(String.class)))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        final TenantContext context = new TenantContext("tenant1", "sub",
                Collections.singleton(TenantPermissions.JOB_RETRY));
        when(tenantAccessService.require(
                any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenReturn(context);

        final UUID jobId = UUID.randomUUID();
        final ConversionJob job = new ConversionJob(
                jobId, "tenant1", "sub", "a.pdf",
                "application/pdf", "hash-a", SIZE, ATTEMPTS);
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(
                eq(jobId), any(String.class)))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant1")
                .header(TenantContext.SUBJECT_ID_HEADER, "sub")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
