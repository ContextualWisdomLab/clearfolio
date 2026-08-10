package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.service.DocumentConversionService;

class AdminControllerDeleteTenantBoundaryTest {

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(conversionService, new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void deleteRejectsMissingTenantAuthorityBeforeMutation() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deleteRejectsMissingDeletePermissionBeforeMutation() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> authorizedHeaders(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deleteUsesTenantScopedMutationAndConcealsMissingOrForeignJobs() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(
                org.mockito.ArgumentMatchers.eq(jobId),
                argThat(context -> context != null && "tenant-a".equals(context.tenantId()))
        )).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> authorizedHeaders(headers, TenantPermissions.JOB_DELETE))
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(
                org.mockito.ArgumentMatchers.eq(jobId),
                argThat(context -> context != null && "tenant-a".equals(context.tenantId()))
        );
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteReturnsNoContentOnlyAfterOwnedTenantScopedMutation() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(
                org.mockito.ArgumentMatchers.eq(jobId),
                argThat(context -> context != null && "tenant-a".equals(context.tenantId()))
        )).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> authorizedHeaders(headers, TenantPermissions.JOB_DELETE))
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(
                org.mockito.ArgumentMatchers.eq(jobId),
                argThat(context -> context != null && "tenant-a".equals(context.tenantId()))
        );
        verify(conversionService, never()).deleteJob(jobId);
    }

    private static void authorizedHeaders(HttpHeaders headers, String permission) {
        headers.set(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.set(TenantContext.SUBJECT_ID_HEADER, "operator-a");
        headers.set(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
