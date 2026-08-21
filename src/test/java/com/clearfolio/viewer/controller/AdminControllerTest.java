package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static final String OTHER_TENANT_ID = "other-tenant";

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(conversionService, new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsOnlyCurrentTenantJobsWhenNoFilterProvided() {
        ConversionJob job1 = defaultTenantJob("a.pdf");
        ConversionJob job2 = defaultTenantJob("b.pdf");
        ConversionJob otherTenantJob = tenantJob(OTHER_TENANT_ID, "private.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, otherTenantJob, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrueWithinCurrentTenant() {
        ConversionJob job1 = defaultTenantJob("a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = defaultTenantJob("b.pdf");
        ConversionJob otherTenantJob = tenantJob(OTHER_TENANT_ID, "private.pdf");
        otherTenantJob.markDeadLettered("failed");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, otherTenantJob, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalseWithinCurrentTenant() {
        ConversionJob job1 = defaultTenantJob("a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = defaultTenantJob("b.pdf");
        ConversionJob otherTenantJob = tenantJob(OTHER_TENANT_ID, "private.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, otherTenantJob, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsReturnsUnauthorizedWithoutTenantClaims() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void getAllJobsReturnsForbiddenWithoutAdminReadPermission() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAuth(headers, TenantPermissions.VIEWER_READ))
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void deleteJobReturnsNoContentForCurrentTenantJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = defaultTenantJob(jobId, "a.pdf");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesCrossTenantJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob(jobId, OTHER_TENANT_ID, "private.pdf");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsForbiddenWithoutAdminWritePermission() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAcceptedForCurrentTenantJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = defaultTenantJob(jobId, "a.pdf");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, TenantContext.DEMO_SUBJECT_ID))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredHidesCrossTenantJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob(jobId, OTHER_TENANT_ID, "private.pdf");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, TenantContext.DEMO_SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, TenantContext.DEMO_SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = defaultTenantJob(jobId, "a.pdf");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, TenantContext.DEMO_SUBJECT_ID))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAuth(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static ConversionJob defaultTenantJob(String fileName) {
        return defaultTenantJob(UUID.randomUUID(), fileName);
    }

    private static ConversionJob defaultTenantJob(UUID jobId, String fileName) {
        return tenantJob(jobId, TenantContext.DEMO_TENANT_ID, fileName);
    }

    private static ConversionJob tenantJob(String tenantId, String fileName) {
        return tenantJob(UUID.randomUUID(), tenantId, fileName);
    }

    private static ConversionJob tenantJob(UUID jobId, String tenantId, String fileName) {
        return new ConversionJob(
                jobId,
                tenantId,
                TenantContext.DEMO_SUBJECT_ID,
                fileName,
                "application/pdf",
                "hash-" + jobId,
                100L,
                3
        );
    }

    private static void addAuth(HttpHeaders headers, String permission) {
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
