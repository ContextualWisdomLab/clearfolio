package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import org.springframework.http.HttpHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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

        TenantContext mockContext = mock(TenantContext.class);
        when(mockContext.tenantId()).thenReturn("buyer-demo");
        when(mockContext.subjectId()).thenReturn("subject-1");
        org.mockito.Mockito.doCallRealMethod().when(tenantAccessService).requireSameTenant(any(), any());
        when(tenantAccessService.require(any(HttpHeaders.class), any(String.class))).thenReturn(mockContext);
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

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
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

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
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

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
        ConversionJob mockJob = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(mockJob));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(new ConversionJob(jobId, "a", "application/pdf", "hash", 100L)));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(new ConversionJob(jobId, "a", "application/pdf", "hash", 100L)));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(new ConversionJob(jobId, "a", "application/pdf", "hash", 100L)));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void deleteJobThrowsWhenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList());

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryThrowsWhenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }
}
