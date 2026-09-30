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
    private static final String SUBJECT_ID = "operator-a";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        AdminController controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsOnlyRequestTenantJobsWhenNoFilterProvided() {
        TenantContext context = allow(TenantPermissions.ADMIN_READ);
        ConversionJob owned = job(TENANT_ID, "a.pdf");
        ConversionJob foreign = job(OTHER_TENANT_ID, "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");

        verify(tenantAccessService)
                .require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
        verify(conversionService).getAllJobs();
        assert context.tenantId().equals(TENANT_ID);
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredTrue() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob deadLettered = job(TENANT_ID, "a.pdf");
        deadLettered.markDeadLettered("failed");
        ConversionJob active = job(TENANT_ID, "b.pdf");
        ConversionJob foreign = job(OTHER_TENANT_ID, "foreign.pdf");
        foreign.markDeadLettered("failed");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(deadLettered, active, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredFalse() {
        allow(TenantPermissions.ADMIN_READ);
        ConversionJob deadLettered = job(TENANT_ID, "a.pdf");
        deadLettered.markDeadLettered("failed");
        ConversionJob active = job(TENANT_ID, "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(deadLettered, active));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsRejectsUnauthorizedWithoutCallingService() {
        deny(TenantPermissions.ADMIN_READ, HttpStatus.UNAUTHORIZED);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void getAllJobsRejectsForbiddenWithoutCallingService() {
        deny(TenantPermissions.ADMIN_READ, HttpStatus.FORBIDDEN);

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void deleteJobUsesTenantScopedDelete() {
        TenantContext context = allow(TenantPermissions.ADMIN_WRITE);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, context)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId, context);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        TenantContext context = allow(TenantPermissions.ADMIN_WRITE);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, context)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobRejectsUnauthorizedWithoutCallingService() {
        deny(TenantPermissions.ADMIN_WRITE, HttpStatus.UNAUTHORIZED);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobRejectsForbiddenWithoutCallingService() {
        deny(TenantPermissions.ADMIN_WRITE, HttpStatus.FORBIDDEN);
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredChecksOwnershipBeforeRetry() {
        TenantContext context = allow(TenantPermissions.ADMIN_WRITE);
        ConversionJob owned = job(TENANT_ID, "a.pdf");
        UUID jobId = owned.getJobId();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(owned));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).requireSameTenant(context, owned);
        verify(conversionService).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobIsMissing() {
        allow(TenantPermissions.ADMIN_WRITE);
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService, never())
                .requireSameTenant(any(TenantContext.class), any(ConversionJob.class));
        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredHidesForeignTenantJob() {
        TenantContext context = allow(TenantPermissions.ADMIN_WRITE);
        ConversionJob foreign = job(OTHER_TENANT_ID, "foreign.pdf");
        UUID jobId = foreign.getJobId();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(foreign));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService).requireSameTenant(context, foreign);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        TenantContext context = allow(TenantPermissions.ADMIN_WRITE);
        ConversionJob owned = job(TENANT_ID, "a.pdf");
        UUID jobId = owned.getJobId();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(owned));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(tenantAccessService).requireSameTenant(context, owned);
    }

    @Test
    void retryDeadLetteredRejectsUnauthorizedWithoutCallingService() {
        deny(TenantPermissions.ADMIN_WRITE, HttpStatus.UNAUTHORIZED);
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredRejectsForbiddenWithoutCallingService() {
        deny(TenantPermissions.ADMIN_WRITE, HttpStatus.FORBIDDEN);
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isForbidden();

        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    private TenantContext allow(String permission) {
        TenantContext context = new TenantContext(TENANT_ID, SUBJECT_ID, Set.of(permission));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(permission)))
                .thenReturn(context);
        return context;
    }

    private void deny(String permission, HttpStatus status) {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(permission)))
                .thenThrow(new ResponseStatusException(status));
    }

    private ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                SUBJECT_ID,
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3
        );
    }
}
