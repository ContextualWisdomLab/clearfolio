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

    private static final String TENANT_ID = "tenant-1";
    private static final String SUBJECT_ID = "subject-1";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        AdminController controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsRequiresAdminReadAndReturnsOwnedJobsWhenNoFilterProvided() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsDoesNotExposeAnotherTenantsJobs() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob owned = jobForTenant(TENANT_ID, "owned.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredTrue() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");
        foreign.markDeadLettered("failed");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredFalse() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsReturnsUnauthorizedWhenAuthenticationFails() {
        deny(TenantPermissions.ADMIN_READ, HttpStatus.UNAUTHORIZED);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void getAllJobsReturnsForbiddenWhenAdminReadIsMissing() {
        deny(TenantPermissions.ADMIN_READ, HttpStatus.FORBIDDEN);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void deleteJobRequiresAdminDeleteAndReturnsNoContentForOwnedJob() {
        allow(TenantPermissions.ADMIN_DELETE);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_DELETE));
        verify(conversionService).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        allow(TenantPermissions.ADMIN_DELETE);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsUnauthorizedWhenAuthenticationFails() {
        deny(TenantPermissions.ADMIN_DELETE, HttpStatus.UNAUTHORIZED);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsForbiddenWhenAdminDeleteIsMissing() {
        deny(TenantPermissions.ADMIN_DELETE, HttpStatus.FORBIDDEN);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredRequiresAdminRetryAndAttributesAuthenticatedSubject() {
        allow(TenantPermissions.ADMIN_RETRY);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_RETRY));
        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredHidesAnotherTenantsJob() {
        allow(TenantPermissions.ADMIN_RETRY);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant("tenant-2", "foreign.pdf")));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(eq(jobId), any());
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenOwnedJobDisappearsBeforeRetry() {
        allow(TenantPermissions.ADMIN_RETRY);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenOwnedJobIsNotEligible() {
        allow(TenantPermissions.ADMIN_RETRY);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void retryDeadLetteredReturnsUnauthorizedWhenAuthenticationFails() {
        deny(TenantPermissions.ADMIN_RETRY, HttpStatus.UNAUTHORIZED);
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(eq(jobId), any());
    }

    @Test
    void retryDeadLetteredReturnsForbiddenWhenAdminRetryIsMissing() {
        deny(TenantPermissions.ADMIN_RETRY, HttpStatus.FORBIDDEN);
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(eq(jobId), any());
    }

    private TenantContext allow(String permission) {
        TenantContext context = new TenantContext(TENANT_ID, SUBJECT_ID, Set.of(permission));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(permission))).thenReturn(context);
        return context;
    }

    private void deny(String permission, HttpStatus status) {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(permission)))
                .thenThrow(new ResponseStatusException(status, "authorization rejected"));
    }

    private static ConversionJob jobForTenant(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "job-subject",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3
        );
    }
}
