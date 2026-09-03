package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.security.CredentialRegistryPort;

class TenantAccessCredentialRegistryBoundaryTest {

    private static final String SECRET = "tenant-claims-secret";

    @Test
    void springRuntimeConstructorResolvesTenantClaimKeyFromRegistry() {
        com.clearfolio.viewer.security.CredentialRegistryPort registry = name -> com.clearfolio.viewer.security.CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET.equals(name)
                ? Optional.of(SECRET)
                : Optional.empty();
        TenantAccessService service = new TenantAccessService(registry, 300L);
        HttpHeaders headers = signedHeaders(Instant.now());

        TenantContext context = service.require(headers, TenantPermissions.JOB_READ);

        assertEquals(TenantContext.DEMO_TENANT_ID, context.tenantId());
    }

    @Test
    void springRuntimeConstructorFailsClosedWhenRegistryHasNoTenantClaimKey() {
        TenantAccessService service = new TenantAccessService((com.clearfolio.viewer.security.CredentialRegistryPort) name -> Optional.empty(), 300L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.require(unsignedHeaders(), TenantPermissions.JOB_READ)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    private static HttpHeaders unsignedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);
        return headers;
    }

    private static HttpHeaders signedHeaders(Instant issuedAt) {
        HttpHeaders headers = unsignedHeaders();
        String issuedAtValue = String.valueOf(issuedAt.getEpochSecond());
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.add(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAtValue);
        headers.add(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAtValue, SECRET)
        );
        return headers;
    }
}
