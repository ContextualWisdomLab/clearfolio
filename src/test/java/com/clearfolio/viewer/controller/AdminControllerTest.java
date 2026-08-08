package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
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
        tenantAccessService = new TenantAccessService();
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "buyer-demo-operator", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:delete")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.anyString())).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.anyString())).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "buyer-demo-operator", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.anyString())).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Clearfolio-Tenant-Id", "buyer-demo")
                .header("X-Clearfolio-Subject-Id", "buyer-demo-operator")
                .header("X-Clearfolio-Permissions", "job:retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
