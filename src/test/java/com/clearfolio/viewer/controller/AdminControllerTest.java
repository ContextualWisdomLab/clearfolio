package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

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
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ))).thenReturn(mockContext);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ))).thenReturn(mockContext);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_READ)
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
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_READ));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ))).thenReturn(mockContext);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_READ)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_WRITE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE))).thenReturn(mockContext);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_WRITE)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_WRITE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE))).thenReturn(mockContext);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_WRITE)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_WRITE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE))).thenReturn(mockContext);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_WRITE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);
        TenantContext mockContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_WRITE));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE))).thenReturn(mockContext);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_WRITE)
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
