package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(
                conversionService, new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsRejectsMissingClaims() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getAllJobsRejectsMissingReadPermission() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "other-tenant", "subject-1", "c.pdf", "application/pdf", "hash-c", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalseAndTenant() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "other-tenant", "subject-1", "c.pdf", "application/pdf", "hash-c", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
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
        when(conversionService.deleteJob(any(UUID.class), any(TenantContext.class))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobRejectsMissingDeletePermission() {
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobReturnsNotFoundWhenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(any(UUID.class), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject-1")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredRejectsMissingRetryPermission() {
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID() + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredHidesOtherTenantJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-2", "subject-2", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenDeletedAfterLookup() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject-1"))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "subject-1")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "tenant-1")
                .header(TenantContext.SUBJECT_ID_HEADER, "subject-1")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
