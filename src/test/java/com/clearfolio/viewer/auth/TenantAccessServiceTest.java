package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Provider;
import java.security.Security;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.model.ConversionJob;

class TenantAccessServiceTest {

    private static final String SECRET = "tenant-claims-secret";
    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

    private final TenantAccessService service = new TenantAccessService();

    @Test
    void requireReturnsContextWhenPermissionIsPresent() {
        TenantContext context = service.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_READ);

        assertEquals(TenantContext.DEMO_TENANT_ID, context.tenantId());
    }

    @Test
    void requireAcceptsSignedGatewayClaimsWhenSecretIsConfigured() {
        TenantAccessService signedService = signedService();
        HttpHeaders headers = signedHeaders(TenantPermissions.JOB_READ, NOW);

        TenantContext context = signedService.require(headers, TenantPermissions.JOB_READ);

        assertEquals(TenantContext.DEMO_TENANT_ID, context.tenantId());
    }

    @Test
    void requireSkipsSignatureValidationWhenSecretIsBlankOrNull() {
        TenantAccessService blankSecret = new TenantAccessService(" ", 300L, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantAccessService nullSecret = new TenantAccessService(null, 300L, Clock.fixed(NOW, ZoneOffset.UTC));

        assertDoesNotThrow(() -> blankSecret.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_READ));
        assertDoesNotThrow(() -> nullSecret.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_READ));
    }

    @Test
    void springConstructorSupportsSignedGatewayClaims() {
        TenantAccessService signedService = new TenantAccessService(SECRET, 300L);

        assertDoesNotThrow(() -> signedService.require(
                signedHeaders(TenantPermissions.JOB_READ, Instant.now()),
                TenantPermissions.JOB_READ
        ));
    }

    @Test
    void requireRejectsMissingSignatureOrTimestampWhenSecretIsConfigured() {
        TenantAccessService signedService = signedService();
        HttpHeaders missingSignature = headers(TenantPermissions.JOB_READ);
        missingSignature.add(TenantContext.CLAIMS_ISSUED_AT_HEADER, String.valueOf(NOW.getEpochSecond()));
        HttpHeaders missingTimestamp = headers(TenantPermissions.JOB_READ);
        missingTimestamp.add(TenantContext.CLAIMS_SIGNATURE_HEADER, "signature");

        ResponseStatusException noSignature = assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(missingSignature, TenantPermissions.JOB_READ)
        );
        ResponseStatusException noTimestamp = assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(missingTimestamp, TenantPermissions.JOB_READ)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, noSignature.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, noTimestamp.getStatusCode());
    }

    @Test
    void requireRejectsInvalidSignature() {
        TenantAccessService signedService = signedService();
        HttpHeaders headers = signedHeaders(TenantPermissions.JOB_READ, NOW);
        headers.set(TenantContext.CLAIMS_SIGNATURE_HEADER, "invalid");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(headers, TenantPermissions.JOB_READ)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void requireRejectsInvalidOrExpiredTimestamp() {
        TenantAccessService signedService = signedService();
        HttpHeaders invalidTimestamp = signedHeaders(TenantPermissions.JOB_READ, NOW);
        invalidTimestamp.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, "not-a-number");
        HttpHeaders expired = signedHeaders(TenantPermissions.JOB_READ, NOW.minusSeconds(301));
        HttpHeaders future = signedHeaders(TenantPermissions.JOB_READ, NOW.plusSeconds(301));
        HttpHeaders ancient = signedHeaders(TenantPermissions.JOB_READ, Long.MIN_VALUE);

        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(invalidTimestamp, TenantPermissions.JOB_READ)
        ).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(expired, TenantPermissions.JOB_READ)
        ).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(future, TenantPermissions.JOB_READ)
        ).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(ancient, TenantPermissions.JOB_READ)
        ).getStatusCode());
    }

    @Test
    void requireUsesZeroSkewFloorForNegativeConfiguredSkew() {
        TenantAccessService signedService = new TenantAccessService(
                SECRET,
                -1L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertDoesNotThrow(() -> signedService.require(
                signedHeaders(TenantPermissions.JOB_READ, NOW),
                TenantPermissions.JOB_READ
        ));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(
                ResponseStatusException.class,
                () -> signedService.require(
                        signedHeaders(TenantPermissions.JOB_READ, NOW.plusSeconds(1)),
                        TenantPermissions.JOB_READ
                )
        ).getStatusCode());
    }

    @Test
    void signClaimsThrowsWhenHmacProviderIsUnavailable() {
        TenantContext context = TenantContext.fromHeaders(headers(TenantPermissions.JOB_READ)).orElseThrow();
        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> TenantAccessService.signClaims(context, String.valueOf(NOW.getEpochSecond()), SECRET)
            );
            assertEquals("tenant claims signing failed", ex.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }
    }

    @Test
    void requireRejectsMissingClaims() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.require(new HttpHeaders(), TenantPermissions.JOB_READ)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void requireRejectsMissingPermission() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_RETRY)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void requireSameTenantRejectsCrossTenantJobAsNotFound() {
        TenantContext context = new TenantContext("tenant-a", "user-1", Set.of(TenantPermissions.JOB_READ));
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-b",
                "user-2",
                "report.docx",
                "application/octet-stream",
                "hash",
                1L,
                1
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSameTenant(context, job)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void requireSameTenantRejectsNullContextOrJobAsNotFound() {
        TenantContext context = new TenantContext("tenant-a", "user-1", Set.of(TenantPermissions.JOB_READ));
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "user-1",
                "report.docx",
                "application/octet-stream",
                "hash",
                1L,
                1
        );

        ResponseStatusException nullContext = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSameTenant(null, job)
        );
        ResponseStatusException nullJob = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSameTenant(context, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, nullContext.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, nullJob.getStatusCode());
    }

    @Test
    void requireSameTenantAcceptsOwnedJob() {
        TenantContext context = new TenantContext("tenant-a", "user-1", Set.of(TenantPermissions.JOB_READ));
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "user-1",
                "report.docx",
                "application/octet-stream",
                "hash",
                1L,
                1
        );

        assertDoesNotThrow(() -> service.requireSameTenant(context, job));
    }

    private static HttpHeaders headers(String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
        return headers;
    }

    private static HttpHeaders signedHeaders(String permissions, Instant issuedAt) {
        return signedHeaders(permissions, issuedAt.getEpochSecond());
    }

    private static HttpHeaders signedHeaders(String permissions, long issuedAtEpoch) {
        HttpHeaders headers = headers(permissions);
        String issuedAtValue = String.valueOf(issuedAtEpoch);
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.add(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAtValue);
        headers.add(TenantContext.CLAIMS_SIGNATURE_HEADER, TenantAccessService.signClaims(
                context,
                issuedAtValue,
                SECRET
        ));
        return headers;
    }

    private static TenantAccessService signedService() {
        return new TenantAccessService(SECRET, 300L, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
