package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

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

        when(tenantAccessService.require(any(HttpHeaders.class), any(String.class)))
                .thenReturn(new TenantContext("tenant", "subject", Set.of()));
        org.mockito.Mockito.doNothing()
                .when(tenantAccessService).requireSameTenant(any(), any());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllTenantJobsWhenNoFilterProvided() {
        ConversionJob job1 = job(UUID.randomUUID(), "tenant", "a.pdf");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant", "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsExcludesOtherTenants() {
        ConversionJob ownJob = job(UUID.randomUUID(), "tenant", "own.pdf");
        ConversionJob foreignJob = job(UUID.randomUUID(), "tenant-b", "foreign.pdf");
        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(ownJob, foreignJob));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("own.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = job(UUID.randomUUID(), "tenant", "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant", "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = job(UUID.randomUUID(), "tenant", "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant", "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId))
                .thenReturn(java.util.Optional.of(job(jobId, "tenant", "file.pdf")));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId))
                .thenReturn(java.util.Optional.of(job(jobId, "tenant", "file.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId))
                .thenReturn(java.util.Optional.of(job(jobId, "tenant", "file.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void getAllJobsRequiresJobReadPermission() {
        deny(TenantPermissions.JOB_READ);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobRequiresJobDeletePermission() {
        deny(TenantPermissions.JOB_DELETE);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredRequiresJobRetryPermission() {
        deny(TenantPermissions.JOB_RETRY);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID() + "/retry")
                .header("X-Test", "test")
                .exchange()
                .expectStatus().isForbidden();
    }

    private void deny(final String permission) {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAccessService).require(
                        any(HttpHeaders.class), eq(permission));
    }

    private static ConversionJob job(
            final UUID id, final String tenantId, final String fileName) {
        return new ConversionJob(
                id,
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                "hash",
                100L,
                3);
    }
}
