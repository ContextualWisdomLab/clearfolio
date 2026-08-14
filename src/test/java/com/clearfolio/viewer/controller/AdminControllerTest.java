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
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsOnlyReturnsJobsOwnedByRequestTenant() {
        tenantAccessService = new TenantAccessService();
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
        ConversionJob owned = ownedJob("tenant-1", "owned.pdf");
        ConversionJob other = ownedJob("tenant-2", "other.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, other));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    @Test
    void deleteJobHidesCrossTenantJobAsNotFound() {
        tenantAccessService = new TenantAccessService();
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(
                org.mockito.ArgumentMatchers.eq(jobId),
                org.mockito.ArgumentMatchers.any(TenantContext.class)))
                .thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredHidesCrossTenantJobAsNotFound() {
        tenantAccessService = new TenantAccessService();
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId))
                .thenReturn(Optional.of(ownedJob("tenant-2", "other.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void adminEndpointsRejectMissingAndInsufficientClaims() {
        tenantAccessService = new TenantAccessService();
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "tenant-1")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }

    private static ConversionJob ownedJob(final String tenantId, final String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject-1",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3);
    }

    private static void addAdminHeaders(
            final org.springframework.http.HttpHeaders headers,
            final String permission) {
        headers.add(TenantContext.TENANT_ID_HEADER, "tenant-1");
        headers.add(TenantContext.SUBJECT_ID_HEADER, "admin-1");
        headers.add(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
