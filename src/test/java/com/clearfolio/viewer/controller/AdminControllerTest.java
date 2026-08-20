package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    private static final String TENANT_ID = "buyer-demo";

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

        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsReturnsEmptyListWhenJobDoesNotBelongToTenant() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1));

        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext("different-tenant", "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(0);

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsReturnsEmptyWhenTenantMatchesButDeadLetteredDoesNot() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1));
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(0);

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsReturnsEmptyWhenTenantDoesNotMatchAndDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1));
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext("different-tenant", "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(0);

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsReturnsEmptyWhenTenantDoesNotMatchAndDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1));
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext("different-tenant", "s", java.util.Set.of()));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(0);

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
        verify(conversionService).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsNotFoundWhenDifferentTenant() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext("different-tenant", "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
        verify(conversionService, never()).deleteJob(any());
    }

    @Test
    void deleteJobReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
        verify(conversionService, never()).deleteJob(any());
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenDifferentTenant() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext("different-tenant", "s", java.util.Set.of()));
        ConversionJob job1 = new ConversionJob(jobId, "a.pdf", "application/pdf", "hash-a", 100L);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job1));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(), any())).thenReturn(new TenantContext(TENANT_ID, "s", java.util.Set.of()));
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
    }
}
