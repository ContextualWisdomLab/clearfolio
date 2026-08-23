package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
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
import com.clearfolio.viewer.auth.TenantPermissions;
import java.util.Collections;
import org.springframework.http.HttpHeaders;

import org.springframework.http.HttpStatus;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;
    private TenantContext mockContext;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        controller = new AdminController(conversionService, tenantAccessService);
        mockContext = new TenantContext("tenant-a", "subject-1", Collections.singleton("dummy"));
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(mockContext);
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "a.pdf",
                "application/pdf",
                "hash-a",
                100L,
                3
        );
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "b.pdf",
                "application/pdf",
                "hash-b",
                100L,
                3
        );
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
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(mockContext);
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "a.pdf",
                "application/pdf",
                "hash-a",
                100L,
                3
        );
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "b.pdf",
                "application/pdf",
                "hash-b",
                100L,
                3
        );

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
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(mockContext);
        ConversionJob job1 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "a.pdf",
                "application/pdf",
                "hash-a",
                100L,
                3
        );
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "b.pdf",
                "application/pdf",
                "hash-b",
                100L,
                3
        );

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
    void deleteJobReturnsNotFoundWhenNotDeleted() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_DELETE))).thenReturn(mockContext);
        when(conversionService.deleteJob(any(), any())).thenReturn(false);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobMissing() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(mockContext);
        when(conversionService.getJob(any())).thenReturn(java.util.Optional.empty());
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteJobReturnsNoContent() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_DELETE))).thenReturn(mockContext);
        when(conversionService.deleteJob(any(), any())).thenReturn(true);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(mockContext);
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "test",
                "pdf",
                "hash",
                100L,
                3
        );
        when(conversionService.getJob(any())).thenReturn(java.util.Optional.of(job));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(eq(jobId), any())).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(mockContext);
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "test",
                "pdf",
                "hash",
                100L,
                3
        );
        when(conversionService.getJob(any())).thenReturn(java.util.Optional.of(job));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(eq(jobId), any())).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(mockContext);
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-1",
                "test",
                "pdf",
                "hash",
                100L,
                3
        );
        when(conversionService.getJob(any())).thenReturn(java.util.Optional.of(job));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(eq(jobId), any())).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
