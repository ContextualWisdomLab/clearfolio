package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.CredentialSnapshot;

class TenantAccessServiceCredentialRegistryTest {

    private static final String SECRET = "tenant-registry-signing-key";
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void registryBackedServiceValidatesClaimsWithPurposeScopedSecretBytes() {
        CredentialRegistry registry = reference -> {
            assertEquals("tenant-claims-signing", reference.credentialName());
            assertEquals(CredentialPurpose.TENANT_CLAIMS_SIGNING, reference.purpose());
            return new CredentialSnapshot(
                    reference.credentialName(),
                    "v1",
                    CredentialPurpose.TENANT_CLAIMS_SIGNING,
                    SECRET.getBytes(StandardCharsets.UTF_8)
            );
        };
        TenantAccessService service = new TenantAccessService(
                registry,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpHeaders headers = signedHeaders();

        assertDoesNotThrow(() -> service.require(headers, TenantPermissions.JOB_READ));
    }

    @Test
    void registryBackedServiceRejectsWrongPurposeSnapshot() {
        CredentialRegistry registry = reference -> new CredentialSnapshot(
                reference.credentialName(),
                "v1",
                CredentialPurpose.ARTIFACT_TOKEN_SIGNING,
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TenantAccessService(registry, 300L, Clock.fixed(NOW, ZoneOffset.UTC))
        );

        assertEquals("tenant claims credential purpose mismatch", exception.getMessage());
    }

    @Test
    void registryBackedServiceRejectsWrongCredentialIdentity() {
        CredentialRegistry registry = reference -> new CredentialSnapshot(
                "different-credential",
                "v1",
                CredentialPurpose.TENANT_CLAIMS_SIGNING,
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TenantAccessService(registry, 300L, Clock.fixed(NOW, ZoneOffset.UTC))
        );

        assertEquals("tenant claims credential purpose mismatch", exception.getMessage());
    }

    @Test
    void registryBackedServiceFailsClosedForMissingAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new TenantAccessService(
                        (CredentialRegistry) null,
                        300L,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new TenantAccessService(
                        reference -> null,
                        300L,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new TenantAccessService(
                        reference -> new CredentialSnapshot(
                                reference.credentialName(),
                                "v1",
                                CredentialPurpose.TENANT_CLAIMS_SIGNING,
                                SECRET.getBytes(StandardCharsets.UTF_8)
                        ),
                        300L,
                        null
                )
        );
    }

    private static HttpHeaders signedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.set(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);
        String issuedAt = String.valueOf(NOW.getEpochSecond());
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, SECRET)
        );
        return headers;
    }
}
