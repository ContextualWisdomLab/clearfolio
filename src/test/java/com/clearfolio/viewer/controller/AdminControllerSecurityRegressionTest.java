package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.security.AuditPseudonymizer;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerSecurityRegressionTest {

    private static final String AUDIT_SECRET =
            "test-secret-12345678901234567890";
    private static final String TENANT_ID = "test-tenant";
    private static final String SUBJECT_ID = "test-subject";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private TenantContext tenantContext;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        ConversionProperties properties = mock(ConversionProperties.class);
        when(properties.getAuditPseudonymSecret()).thenReturn(AUDIT_SECRET);
        when(properties.getAuditPseudonymKeyVersion()).thenReturn("v1");

        tenantContext = new TenantContext(TENANT_ID, SUBJECT_ID, Set.of());
        when(tenantAccessService.require(any(), any())).thenReturn(tenantContext);

        AdminController controller = new AdminController(
                conversionService,
                tenantAccessService,
                properties);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listUsesReadPermissionAndFiltersOtherTenant() {
        ConversionJob tenantJob = job(UUID.randomUUID(), TENANT_ID);
        ConversionJob otherTenantJob = job(UUID.randomUUID(), "other-tenant");
        when(conversionService.getAllJobs())
                .thenReturn(List.of(tenantJob, otherTenantJob));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1);

        verify(tenantAccessService).require(
                any(), eq(TenantPermissions.JOB_READ));
    }

    @Test
    void deleteUsesPermissionAndTenantAwareMutationBoundary() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, TENANT_ID);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(tenantAccessService).require(
                any(), eq(TenantPermissions.JOB_DELETE));
        verify(tenantAccessService).requireSameTenant(tenantContext, job);
        verify(conversionService).deleteJob(jobId, tenantContext);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteReturnsNotFoundWhenTenantAwareMutationLosesJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, TENANT_ID);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(
                any(), eq(TenantPermissions.JOB_DELETE));
        verify(tenantAccessService).requireSameTenant(tenantContext, job);
        verify(conversionService).deleteJob(jobId, tenantContext);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryUsesPermissionTenantGuardAndSeparateAuditDomain() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = job(jobId, TENANT_ID);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(eq(jobId), any()))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).require(
                any(), eq(TenantPermissions.JOB_RETRY));
        verify(tenantAccessService).requireSameTenant(tenantContext, job);
        ArgumentCaptor<String> operatorId = ArgumentCaptor.forClass(String.class);
        verify(conversionService).retryDeadLettered(
                eq(jobId), operatorId.capture());

        String approverFingerprint = new AuditPseudonymizer(AUDIT_SECRET, "v1")
                .fingerprint(SUBJECT_ID);
        assertNotEquals(approverFingerprint, operatorId.getValue());
    }

    @Test
    void crossTenantDeleteFailsBeforeAnyMutation() {
        UUID jobId = UUID.randomUUID();
        ConversionJob otherTenantJob = job(jobId, "other-tenant");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(otherTenantJob));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService)
                .requireSameTenant(tenantContext, otherTenantJob);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
        verify(conversionService, never()).deleteJob(jobId, tenantContext);
    }

    @Test
    void crossTenantRetryFailsBeforeAnyMutation() {
        UUID jobId = UUID.randomUUID();
        ConversionJob otherTenantJob = job(jobId, "other-tenant");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(otherTenantJob));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService)
                .requireSameTenant(tenantContext, otherTenantJob);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(tenantAccessService).require(
                any(), eq(TenantPermissions.JOB_RETRY));
        verify(conversionService, never()).retryDeadLettered(eq(jobId), any());
    }

    private ConversionJob job(final UUID jobId, final String tenantId) {
        return new ConversionJob(
                jobId,
                tenantId,
                SUBJECT_ID,
                "document.pdf",
                "application/pdf",
                "hash",
                100L,
                3);
    }
}
