package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

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
    void getAllJobsRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getAllJobsRequiresAdminPermission() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addTenantHeaders(headers, TENANT_A, "admin", TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsReturnsOnlyJobsOwnedByRequestTenant() {
        ConversionJob owned = job(TENANT_A, "owned.pdf");
        ConversionJob foreign = job(TENANT_B, "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    @Test
    void getAllJobsAppliesDeadLetterFilterAfterTenantIsolation() {
        ConversionJob ownedDeadLettered = job(TENANT_A, "owned-dead.pdf");
        ownedDeadLettered.markDeadLettered("failed");
        ConversionJob ownedActive = job(TENANT_A, "owned-active.pdf");
        ConversionJob foreignDeadLettered = job(TENANT_B, "foreign-dead.pdf");
        foreignDeadLettered.markDeadLettered("failed");
        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(ownedDeadLettered, ownedActive, foreignDeadLettered));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned-dead.pdf");

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned-active.pdf");
    }

    @Test
    void deleteJobUsesTenantScopedServiceBoundary() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(
                eq(jobId),
                argThat(context -> TENANT_A.equals(context.tenantId()))
        );
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedForOwnedJobAndPreservesOperatorIdentity() {
        UUID jobId = UUID.randomUUID();
        String operatorId = "operator-7";
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(jobId, TENANT_A, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, operatorId)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TENANT_A, operatorId))
                .exchange()
                .expectStatus().isAccepted();

        verify(conversionService).retryDeadLettered(jobId, operatorId);
    }

    @Test
    void retryDeadLetteredHidesForeignJobBeforeMutation() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(jobId, TENANT_B, "foreign.pdf")));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDisappearsBeforeMutation() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(jobId, TENANT_A, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(jobId, TENANT_A, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TENANT_A))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return job(UUID.randomUUID(), tenantId, fileName);
    }

    private static ConversionJob job(UUID jobId, String tenantId, String fileName) {
        return new ConversionJob(
                jobId,
                tenantId,
                "owner",
                fileName,
                "application/pdf",
                "hash-" + jobId,
                100L,
                3
        );
    }

    private static void addAdminHeaders(org.springframework.http.HttpHeaders headers, String tenantId) {
        addAdminHeaders(headers, tenantId, "admin");
    }

    private static void addAdminHeaders(
            org.springframework.http.HttpHeaders headers,
            String tenantId,
            String subjectId) {
        addTenantHeaders(headers, tenantId, subjectId, TenantPermissions.ADMIN_ACCESS);
    }

    private static void addTenantHeaders(
            org.springframework.http.HttpHeaders headers,
            String tenantId,
            String subjectId,
            String permissions) {
        headers.add(TenantContext.TENANT_ID_HEADER, tenantId);
        headers.add(TenantContext.SUBJECT_ID_HEADER, subjectId);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
    }
}
