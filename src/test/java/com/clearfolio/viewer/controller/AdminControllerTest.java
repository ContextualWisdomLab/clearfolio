package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
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

    private static final String TENANT_ID = "tenant-a";
    private static final String OTHER_TENANT_ID = "tenant-b";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;
    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        tenantContext = new TenantContext(
                TENANT_ID,
                "subject",
                Set.of(TenantPermissions.ADMIN_READ, TenantPermissions.ADMIN_WRITE));
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsForbiddenWhenNoPermission() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllJobsReturnsAllOwnedJobsWhenNoFilterProvided() {
        allowRead();
        ConversionJob job1 = job(TENANT_ID, "a.pdf", "hash-a");
        ConversionJob job2 = job(TENANT_ID, "b.pdf", "hash-b");
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
    void getAllJobsDoesNotExposeOtherTenantJobs() {
        allowRead();
        ConversionJob owned = job(TENANT_ID, "owned.pdf", "owned-hash");
        ConversionJob foreign = job(OTHER_TENANT_ID, "foreign.pdf", "foreign-hash");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        allowRead();
        ConversionJob job1 = job(TENANT_ID, "a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(TENANT_ID, "b.pdf", "hash-b");

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
        allowRead();
        ConversionJob job1 = job(TENANT_ID, "a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(TENANT_ID, "b.pdf", "hash-b");

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
    void deleteJobReturnsForbiddenWhenNoPermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsNoContent() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobHidesForeignTenantJob() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsForbiddenWhenNoPermission() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredHidesForeignTenantJob() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        ConversionJob foreign = job(OTHER_TENANT_ID, "foreign.pdf", "foreign-hash");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(foreign));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService).requireSameTenant(tenantContext, foreign);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        ConversionJob owned = job(TENANT_ID, "owned.pdf", "owned-hash");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(owned));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenOwnedJobDisappearsBeforeRetry() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        ConversionJob owned = job(TENANT_ID, "owned.pdf", "owned-hash");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(owned));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        allowWrite();
        UUID jobId = UUID.randomUUID();
        ConversionJob owned = job(TENANT_ID, "owned.pdf", "owned-hash");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(owned));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private void allowRead() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(tenantContext);
    }

    private void allowWrite() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
    }

    private ConversionJob job(String tenantId, String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                hash,
                100L,
                3);
    }
}
