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

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String ADMIN_SUBJECT = "admin-a";

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
    void getAllJobsReturnsOnlyAuthorizedTenantJobsWhenNoFilterProvided() {
        TenantContext context = context(TenantPermissions.ADMIN_READ);
        ConversionJob job1 = job(TENANT_A, "a.pdf");
        ConversionJob job2 = job(TENANT_A, "b.pdf");
        ConversionJob foreign = job(TENANT_B, "foreign.pdf");

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(context);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, foreign, job2));

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
    void getAllJobsFiltersByDeadLetteredTrueWithinAuthorizedTenant() {
        TenantContext context = context(TenantPermissions.ADMIN_READ);
        ConversionJob deadLettered = job(TENANT_A, "a.pdf");
        deadLettered.markDeadLettered("failed");
        ConversionJob live = job(TENANT_A, "b.pdf");
        ConversionJob foreignDeadLettered = job(TENANT_B, "foreign.pdf");
        foreignDeadLettered.markDeadLettered("failed");

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(context);
        when(conversionService.getAllJobs())
                .thenReturn(Arrays.asList(deadLettered, live, foreignDeadLettered));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalseWithinAuthorizedTenant() {
        TenantContext context = context(TenantPermissions.ADMIN_READ);
        ConversionJob deadLettered = job(TENANT_A, "a.pdf");
        deadLettered.markDeadLettered("failed");
        ConversionJob live = job(TENANT_A, "b.pdf");
        ConversionJob foreignLive = job(TENANT_B, "foreign.pdf");

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(context);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(deadLettered, live, foreignLive));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobUsesTenantScopedServiceBoundary() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.deleteJob(jobId, context)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId, context);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrForeignTenantJob() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.deleteJob(jobId, context)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredUsesTenantScopedOwnershipAndSubject() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        ConversionJob ownedJob = job(TENANT_A, "a.pdf");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(ownedJob));
        when(conversionService.retryDeadLettered(jobId, ADMIN_SUBJECT))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).requireSameTenant(context, ownedJob);
        verify(conversionService).retryDeadLettered(jobId, ADMIN_SUBJECT);
        verify(conversionService, never()).retryDeadLettered(jobId, "admin");
    }

    @Test
    void retryDeadLetteredHidesForeignTenantJob() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        ConversionJob foreignJob = job(TENANT_B, "foreign.pdf");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(foreignJob));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService).requireSameTenant(context, foreignJob);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(eq(jobId), any(String.class));
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(eq(jobId), any(String.class));
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = context(TenantPermissions.ADMIN_WRITE);
        ConversionJob ownedJob = job(TENANT_A, "a.pdf");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(context);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(ownedJob));
        when(conversionService.retryDeadLettered(jobId, ADMIN_SUBJECT))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void getAllJobsFailsWhenUnauthorized() {
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobFailsWhenUnauthorized() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();
    }

    private static TenantContext context(String permission) {
        return new TenantContext(TENANT_A, ADMIN_SUBJECT, Set.of(permission));
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "submitter",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3
        );
    }
}
