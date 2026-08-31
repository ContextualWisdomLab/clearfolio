package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Security regressions for administrator claim verification.
 */
class TenantAccessServiceAdminAuthenticationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void requireSignedRejectsUnsignedModeEvenWhenHeadersClaimPermission() {
        TenantAccessService blankSecret = new TenantAccessService(
                " ",
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        TenantAccessService nullSecret = new TenantAccessService(
                null,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ResponseStatusException blankFailure = assertThrows(
                ResponseStatusException.class,
                () -> blankSecret.requireSigned(
                        unsignedHeaders(TenantPermissions.JOB_DELETE),
                        TenantPermissions.JOB_DELETE
                )
        );
        ResponseStatusException nullFailure = assertThrows(
                ResponseStatusException.class,
                () -> nullSecret.requireSigned(
                        unsignedHeaders(TenantPermissions.JOB_RETRY),
                        TenantPermissions.JOB_RETRY
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, blankFailure.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, nullFailure.getStatusCode());
    }

    @Test
    void requireSignedAcceptsValidSignedClaims() {
        TenantAccessService service = new TenantAccessService(
                SECRET,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpHeaders headers = unsignedHeaders(TenantPermissions.JOB_READ);
        String issuedAt = String.valueOf(NOW.getEpochSecond());
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.add(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.add(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, SECRET)
        );

        TenantContext verified = service.requireSigned(headers, TenantPermissions.JOB_READ);

        assertEquals(TenantContext.DEMO_TENANT_ID, verified.tenantId());
    }

    private static HttpHeaders unsignedHeaders(String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permission);
        return headers;
    }
}
