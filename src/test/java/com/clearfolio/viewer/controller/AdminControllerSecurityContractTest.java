package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Security contract regressions for administrator operations.
 */
class AdminControllerSecurityContractTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private TenantContext tenantContext;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        ConversionProperties properties = mock(ConversionProperties.class);
        when(properties.getAuditPseudonymSecret())
                .thenReturn("test-secret-12345678901234567890");
        when(properties.getAuditPseudonymKeyVersion()).thenReturn("v1");

        tenantContext = new TenantContext(
                "tenant-a",
                "operator-a",
                Set.of(
                        TenantPermissions.JOB_READ,
                        TenantPermissions.JOB_DELETE,
                        TenantPermissions.JOB_RETRY
                )
        );
        when(tenantAccessService.requireSigned(any(), any())).thenReturn(tenantContext);

        AdminController controller = new AdminController(
                conversionService,
                tenantAccessService,
                properties
        );
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listRequiresSignedReadClaims() {
        when(conversionService.getAllJobs()).thenReturn(List.of());

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk();

        verify(tenantAccessService).requireSigned(any(), eq(TenantPermissions.JOB_READ));
        verify(tenantAccessService, never()).require(any(), any());
    }

    @Test
    void deleteUsesTenantScopedMutationWithoutIdOnlyFallback() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(tenantAccessService).requireSigned(any(), eq(TenantPermissions.JOB_DELETE));
        verify(conversionService).deleteJob(jobId, tenantContext);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteReturnsNotFoundWhenTenantScopedMutationRejectsTarget() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, tenantContext)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(jobId, tenantContext);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryUsesTenantScopedMutationWithoutIdOnlyFallback() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(eq(jobId), eq(tenantContext), any()))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).requireSigned(any(), eq(TenantPermissions.JOB_RETRY));
        verify(conversionService).retryDeadLettered(eq(jobId), eq(tenantContext), any());
        verify(conversionService, never()).retryDeadLettered(eq(jobId), any(String.class));
    }

    @Test
    void retryMapsTenantScopedNotFoundAndConflictOutcomes() {
        UUID missingJobId = UUID.randomUUID();
        UUID ineligibleJobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(eq(missingJobId), eq(tenantContext), any()))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);
        when(conversionService.retryDeadLettered(eq(ineligibleJobId), eq(tenantContext), any()))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + missingJobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + ineligibleJobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
