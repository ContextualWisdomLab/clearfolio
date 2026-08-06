package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Defines the fail-closed signed-claim contract for privileged endpoints.
 */
class TenantAccessServiceStrictClaimsTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void requireSignedRejectsMissingVerifierSecret() {
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

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(
                ResponseStatusException.class,
                () -> blankSecret.requireSigned(
                        unsignedHeaders(TenantPermissions.ADMIN_READ),
                        TenantPermissions.ADMIN_READ
                )
        ).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(
                ResponseStatusException.class,
                () -> nullSecret.requireSigned(
                        unsignedHeaders(TenantPermissions.ADMIN_READ),
                        TenantPermissions.ADMIN_READ
                )
        ).getStatusCode());
    }

    @Test
    void requireSignedRejectsWeakConfiguredVerifierSecret() {
        TenantAccessService weakSecret = new TenantAccessService(
                "short-secret",
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> weakSecret.requireSigned(
                        unsignedHeaders(TenantPermissions.ADMIN_READ),
                        TenantPermissions.ADMIN_READ
                )
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void requireSignedAcceptsStrongFreshSignedClaims() {
        TenantAccessService service = new TenantAccessService(
                STRONG_SECRET,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpHeaders headers = signedHeaders(TenantPermissions.ADMIN_READ);

        TenantContext context = assertDoesNotThrow(
                () -> service.requireSigned(headers, TenantPermissions.ADMIN_READ)
        );

        assertEquals(TenantContext.DEMO_TENANT_ID, context.tenantId());
        assertEquals(TenantContext.DEMO_SUBJECT_ID, context.subjectId());
    }

    private static HttpHeaders unsignedHeaders(String permission) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.set(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.set(TenantContext.PERMISSIONS_HEADER, permission);
        return headers;
    }

    private static HttpHeaders signedHeaders(String permission) {
        HttpHeaders headers = unsignedHeaders(permission);
        String issuedAt = Long.toString(NOW.getEpochSecond());
        TenantContext context = new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(permission)
        );
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, STRONG_SECRET)
        );
        return headers;
    }
}
