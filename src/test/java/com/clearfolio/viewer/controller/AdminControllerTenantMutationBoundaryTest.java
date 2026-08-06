package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.audit.AdministrativeAuditLogger;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.security.AuditPseudonymizer;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Verifies that administrator mutations cross a tenant-scoped service boundary.
 *
 * <p>The controller must not authorize by reading an object and then invoke an
 * unscoped mutation in a separate step. Tenant ownership is part of the service
 * mutation contract so non-HTTP callers and future persistence adapters cannot
 * bypass or race the controller-level check.</p>
 */
class AdminControllerTenantMutationBoundaryTest {

    private static final String CLAIM_SECRET = "0123456789abcdef".repeat(2);
    private static final String AUDIT_SECRET = "0123456789abcdef".repeat(2);
    private static final String TENANT_ID = "tenant-north";
    private static final String SUBJECT_ID = "administrator@example.com";

    @Test
    void deleteUsesTheTenantScopedServiceMutationWithoutASeparateLookup() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(any(UUID.class), any(TenantContext.class)))
                .thenReturn(true);

        WebTestClient client = client(conversionService);

        client.delete()
                .uri("/api/v1/admin/convert/jobs/{jobId}", jobId)
                .headers(target -> target.addAll(signedHeaders(TenantPermissions.ADMIN_WRITE)))
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(
                eq(jobId),
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryUsesTheTenantScopedServiceMutationWithoutASeparateLookup() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        UUID jobId = UUID.randomUUID();
        String actorFingerprint = AuditPseudonymizer.forAdministrativeActor(
                AUDIT_SECRET,
                "admin-v1"
        ).fingerprint(SUBJECT_ID);
        when(conversionService.retryDeadLettered(
                any(UUID.class),
                any(TenantContext.class),
                any(String.class)
        )).thenReturn(RetryDeadLetterResult.ACCEPTED);

        WebTestClient client = client(conversionService);

        client.post()
                .uri("/api/v1/admin/convert/jobs/{jobId}/retry", jobId)
                .headers(target -> target.addAll(signedHeaders(TenantPermissions.ADMIN_WRITE)))
                .exchange()
                .expectStatus().isAccepted();

        verify(conversionService).retryDeadLettered(
                eq(jobId),
                argThat(context -> TENANT_ID.equals(context.tenantId())),
                eq(actorFingerprint)
        );
        verify(conversionService, never()).getJob(jobId);
        verify(conversionService, never()).retryDeadLettered(any(UUID.class), any(String.class));
    }

    private static WebTestClient client(DocumentConversionService conversionService) {
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret(AUDIT_SECRET);
        properties.setAuditPseudonymKeyVersion("admin-v1");
        AdminController controller = new AdminController(
                conversionService,
                new TenantAccessService(CLAIM_SECRET, 300L),
                new AdministrativeAuditLogger(properties)
        );
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static HttpHeaders signedHeaders(String... permissions) {
        LinkedHashSet<String> permissionSet = new LinkedHashSet<>(List.of(permissions));
        TenantContext context = new TenantContext(TENANT_ID, SUBJECT_ID, permissionSet);
        String issuedAt = Long.toString(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, TENANT_ID);
        headers.set(TenantContext.SUBJECT_ID_HEADER, SUBJECT_ID);
        headers.set(TenantContext.PERMISSIONS_HEADER, String.join(",", permissions));
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, CLAIM_SECRET)
        );
        return headers;
    }
}
