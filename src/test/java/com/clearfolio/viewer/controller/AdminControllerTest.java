package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import com.clearfolio.viewer.security.RetryOperatorIdentityPort;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private static final String RETRY_OPERATOR_ID = "v7:6b0b51a6dd5b6deef1f7457e9ae23b44";
    private static final String UNKEYED_USER_ONE_SHA256 =
            "b32817bf034f5dcb3ac5f1e8dc3a19fc82dd24409bb10bc1a1f0a2dbb059f131";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private RetryOperatorIdentityPort retryOperatorIdentity;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        retryOperatorIdentity = mock(RetryOperatorIdentityPort.class);
        when(retryOperatorIdentity.pseudonymize("user-1")).thenReturn(RETRY_OPERATOR_ID);
        AdminController controller = new AdminController(
                conversionService,
                tenantAccessService,
                retryOperatorIdentity
        );
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsUsesTenantScopedApplicationQueryWhenNoFilterProvided() {
        TenantContext context = context(TenantPermissions.JOB_READ);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = job(UUID.randomUUID(), "tenant-1", "a.pdf");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant-1", "b.pdf");
        when(conversionService.getJobsForTenant(context)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        verify(conversionService).getJobsForTenant(context);
        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void getAllJobsFiltersDeadLetteredTrueWithinTenantScopedResult() {
        TenantContext context = context(TenantPermissions.JOB_READ);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = job(UUID.randomUUID(), "tenant-1", "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant-1", "b.pdf");
        when(conversionService.getJobsForTenant(context)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersDeadLetteredFalseWithinTenantScopedResult() {
        TenantContext context = context(TenantPermissions.JOB_READ);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ))).thenReturn(context);

        ConversionJob job1 = job(UUID.randomUUID(), "tenant-1", "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = job(UUID.randomUUID(), "tenant-1", "b.pdf");
        when(conversionService.getJobsForTenant(context)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFailsClosedBeforeApplicationQueryWhenAuthorizationFails() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth token required"));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(conversionService, never()).getJobsForTenant(any());
        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void deleteJobUsesTenantScopedCommandAndReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.JOB_DELETE);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(context);
        when(conversionService.deleteJob(jobId, context)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId, context);
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrCrossTenantResource() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.JOB_DELETE);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_DELETE))).thenReturn(context);
        when(conversionService.deleteJob(jobId, context)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(jobId, context);
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredUsesKeyedIdentityAndTenantScopedCommand() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.JOB_RETRY);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        when(conversionService.retryDeadLettered(jobId, RETRY_OPERATOR_ID, context))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(retryOperatorIdentity).pseudonymize("user-1");
        verify(conversionService).retryDeadLettered(jobId, RETRY_OPERATOR_ID, context);
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(jobId, RETRY_OPERATOR_ID);
        assertNotEquals(UNKEYED_USER_ONE_SHA256, RETRY_OPERATOR_ID);
    }

    @Test
    void retryDeadLetteredHidesMissingOrCrossTenantResource() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.JOB_RETRY);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        when(conversionService.retryDeadLettered(jobId, RETRY_OPERATOR_ID, context))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).retryDeadLettered(jobId, RETRY_OPERATOR_ID, context);
        verify(conversionService, never()).getJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.JOB_RETRY);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.JOB_RETRY))).thenReturn(context);
        when(conversionService.retryDeadLettered(jobId, RETRY_OPERATOR_ID, context))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static TenantContext context(final String permission) {
        return new TenantContext("tenant-1", "user-1", java.util.Set.of(permission));
    }

    private static ConversionJob job(final UUID jobId, final String tenantId, final String fileName) {
        return new ConversionJob(
                jobId,
                tenantId,
                "user-1",
                fileName,
                "application/pdf",
                "hash",
                100L,
                3
        );
    }
}
