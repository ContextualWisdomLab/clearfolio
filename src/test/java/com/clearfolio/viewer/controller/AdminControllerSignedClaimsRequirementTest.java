package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.audit.AdministrativeAuditLogger;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * Proves privileged endpoints never fall back to unsigned demo-header mode.
 */
class AdminControllerSignedClaimsRequirementTest {

    private static final String AUDIT_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void administrativeListIsUnavailableWithoutAConfiguredSignedClaimVerifier() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret(AUDIT_SECRET);
        properties.setAuditPseudonymKeyVersion("admin-v1");
        AdminController controller = new AdminController(
                conversionService,
                new TenantAccessService("", 300L),
                new AdministrativeAuditLogger(properties)
        );
        WebTestClient client = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
        HttpHeaders unsignedHeaders = new HttpHeaders();
        unsignedHeaders.set(TenantContext.TENANT_ID_HEADER, "tenant-north");
        unsignedHeaders.set(TenantContext.SUBJECT_ID_HEADER, "administrator@example.com");
        unsignedHeaders.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_READ);

        client.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(target -> target.addAll(unsignedHeaders))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");

        verifyNoInteractions(conversionService);
    }
}
