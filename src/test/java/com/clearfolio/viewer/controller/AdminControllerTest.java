package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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

    @Test
    void getAllJobsBlocksWhenUnauthorized() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deleteJobBlocksWhenUnauthorized() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void retryDeadLetteredBlocksWhenUnauthorized() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID() + "/retry")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }


    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_READ)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_READ)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_READ)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_DELETE)));
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_RETRY)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_RETRY)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.JOB_RETRY)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
