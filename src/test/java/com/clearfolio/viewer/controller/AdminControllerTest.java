package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;
    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        tenantContext = mock(TenantContext.class);

        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        when(tenantContext.tenantId()).thenReturn("default");

        ConversionJob job1 = mock(ConversionJob.class);
        when(job1.belongsToTenant("default")).thenReturn(true);
        when(job1.getOriginalFileName()).thenReturn("a.pdf");
        when(job1.getJobId()).thenReturn(UUID.randomUUID());
        when(job1.getTenantId()).thenReturn("default");
        when(job1.getStatus()).thenReturn(ConversionJobStatus.SUBMITTED);
        when(job1.getCreatedAt()).thenReturn(Instant.now());

        ConversionJob job2 = mock(ConversionJob.class);
        when(job2.belongsToTenant("default")).thenReturn(true);
        when(job2.getOriginalFileName()).thenReturn("b.pdf");
        when(job2.getJobId()).thenReturn(UUID.randomUUID());
        when(job2.getTenantId()).thenReturn("default");
        when(job2.getStatus()).thenReturn(ConversionJobStatus.SUBMITTED);
        when(job2.getCreatedAt()).thenReturn(Instant.now());

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2);
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        when(tenantContext.tenantId()).thenReturn("default");

        ConversionJob job1 = mock(ConversionJob.class);
        when(job1.belongsToTenant("default")).thenReturn(true);
        when(job1.isDeadLettered()).thenReturn(true);
        when(job1.getOriginalFileName()).thenReturn("a.pdf");
        when(job1.getJobId()).thenReturn(UUID.randomUUID());
        when(job1.getTenantId()).thenReturn("default");
        when(job1.getStatus()).thenReturn(ConversionJobStatus.FAILED);
        when(job1.getCreatedAt()).thenReturn(Instant.now());

        ConversionJob job2 = mock(ConversionJob.class);
        when(job2.belongsToTenant("default")).thenReturn(true);
        when(job2.isDeadLettered()).thenReturn(false);
        when(job2.getOriginalFileName()).thenReturn("b.pdf");
        when(job2.getJobId()).thenReturn(UUID.randomUUID());
        when(job2.getTenantId()).thenReturn("default");
        when(job2.getStatus()).thenReturn(ConversionJobStatus.FAILED);
        when(job2.getCreatedAt()).thenReturn(Instant.now());

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1);
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_READ))).thenReturn(tenantContext);
        when(tenantContext.tenantId()).thenReturn("default");

        ConversionJob job1 = mock(ConversionJob.class);
        when(job1.belongsToTenant("default")).thenReturn(true);
        when(job1.isDeadLettered()).thenReturn(true);
        when(job1.getOriginalFileName()).thenReturn("a.pdf");
        when(job1.getJobId()).thenReturn(UUID.randomUUID());
        when(job1.getTenantId()).thenReturn("default");
        when(job1.getStatus()).thenReturn(ConversionJobStatus.FAILED);
        when(job1.getCreatedAt()).thenReturn(Instant.now());

        ConversionJob job2 = mock(ConversionJob.class);
        when(job2.belongsToTenant("default")).thenReturn(true);
        when(job2.isDeadLettered()).thenReturn(false);
        when(job2.getOriginalFileName()).thenReturn("b.pdf");
        when(job2.getJobId()).thenReturn(UUID.randomUUID());
        when(job2.getTenantId()).thenReturn("default");
        when(job2.getStatus()).thenReturn(ConversionJobStatus.SUBMITTED);
        when(job2.getCreatedAt()).thenReturn(Instant.now());

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1);
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_DELETE))).thenReturn(tenantContext);
        ConversionJob job = mock(ConversionJob.class);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        when(tenantContext.subjectId()).thenReturn("operator-1");

        ConversionJob job = mock(ConversionJob.class);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        when(tenantContext.subjectId()).thenReturn("operator-1");

        ConversionJob job = mock(ConversionJob.class);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), eq(TenantPermissions.JOB_RETRY))).thenReturn(tenantContext);
        when(tenantContext.subjectId()).thenReturn("operator-1");

        ConversionJob job = mock(ConversionJob.class);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any(String.class))).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
