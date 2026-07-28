package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import org.springframework.http.HttpHeaders;
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
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_READ)
        );
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_READ)
        );
    }

    @Test
    void getAllJobsRequiresAdminReadPermission() {
        when(tenantAccessService.require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_READ)
        )).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "missing permission"));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_READ)
        );
    }

    @Test
    void deleteJobRequiresAdminWritePermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        )).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "missing permission"));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isNoContent();

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        );
    }

    @Test
    void retryDeadLetteredRequiresAdminWritePermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        )).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "missing permission"));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isAccepted();

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        );
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isNotFound();

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        );
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Dummy", "dummy")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer

        org.mockito.Mockito.verify(tenantAccessService).require(
                org.mockito.ArgumentMatchers.any(HttpHeaders.class),
                org.mockito.ArgumentMatchers.eq(com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE)
        );
    }
}
