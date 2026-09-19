package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
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
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "a.pdf", "application/pdf",
                "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "b.pdf", "application/pdf",
                "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(
                UUID.randomUUID(), "tenant-2", "sub-1", "c.pdf", "application/pdf",
                "hash-c", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3));
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:read"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:read")
        )).thenReturn(ctx);

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
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "a.pdf", "application/pdf",
                "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "b.pdf", "application/pdf",
                "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:read"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:read")
        )).thenReturn(ctx);

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
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "a.pdf", "application/pdf",
                "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(), "tenant-1", "sub-1", "b.pdf", "application/pdf",
                "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:read"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:read")
        )).thenReturn(ctx);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:write"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:write")
        )).thenReturn(ctx);
        when(conversionService.deleteJob(
                ArgumentMatchers.eq(jobId),
                ArgumentMatchers.eq(ctx)
        )).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobReturnsNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:write"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:write")
        )).thenReturn(ctx);
        when(conversionService.deleteJob(
                ArgumentMatchers.eq(jobId),
                ArgumentMatchers.eq(ctx)
        )).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId, "tenant-1", "sub-1", "a.pdf", "application/pdf",
                "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:write"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:write")
        )).thenReturn(ctx);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:write"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:write")
        )).thenReturn(ctx);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId, "tenant-1", "sub-1", "a.pdf", "application/pdf",
                "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);
        TenantContext ctx = new TenantContext("tenant-1", "sub-1", Set.of("admin:write"));
        when(tenantAccessService.require(
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.eq("admin:write")
        )).thenReturn(ctx);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
