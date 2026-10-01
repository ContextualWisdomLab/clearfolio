package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Arrays;
import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.auth.TenantContext;

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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
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
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenReturn(new TenantContext("t1", "s1", Set.of(TenantPermissions.ADMIN_OPERATE)));
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }

    @Test
    void getAllJobsRequiresAuthorization() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobRequiresAuthorization() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredRequiresAuthorization() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_OPERATE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();
    }
}