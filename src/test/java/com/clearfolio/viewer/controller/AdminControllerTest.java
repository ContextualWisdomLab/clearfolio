package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentMatchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

import static org.mockito.Mockito.doThrow;

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
        tenantContext = new TenantContext("tenant", "subject", Set.of(TenantPermissions.ADMIN_READ, TenantPermissions.ADMIN_WRITE));
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsForbiddenWhenNoPermission() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);
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
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);

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
    void getAllJobsHidesForeignTenantJobAndFiltersDeadLettered() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "other", "other", "c.pdf", "application/pdf", "hash-c", 100L, 3);
        job3.markDeadLettered("failed");
        ConversionJob job4 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "d.pdf", "application/pdf", "hash-d", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3, job4));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("d.pdf");
    }

    @Test
    void getAllJobsHidesForeignTenantJobAndFiltersDeadLetteredNull() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "other", "other", "c.pdf", "application/pdf", "hash-c", 100L, 3);
        job3.markDeadLettered("failed");
        ConversionJob job4 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "d.pdf", "application/pdf", "hash-d", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3, job4));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(3)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf")
                .jsonPath("$.jobs[2].fileName").isEqualTo("d.pdf");
    }

    @Test
    void getAllJobsHidesForeignTenantJobAndFiltersDeadLettered2() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "b.pdf", "application/pdf", "hash-b", 100L, 3);
        ConversionJob job3 = new ConversionJob(UUID.randomUUID(), "other", "other", "c.pdf", "application/pdf", "hash-c", 100L, 3);
        job3.markDeadLettered("failed");
        ConversionJob job4 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "d.pdf", "application/pdf", "hash-d", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, job3, job4));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsHidesForeignTenantJob() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "other-tenant", "other-subject", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void deleteJobHidesForeignTenantJob() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredHidesForeignTenantJob() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(tenantAccessService).requireSameTenant(tenantContext, job);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteJobReturnsNoContent() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        when(tenantAccessService.require(ArgumentMatchers.any(HttpHeaders.class), ArgumentMatchers.eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, tenantContext.tenantId(), tenantContext.subjectId(), "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }
}
