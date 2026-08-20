package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.CredentialSnapshot;

class TenantAccessServiceCredentialRegistryTest {

    private static final String SECRET = "t".repeat(32);
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void registryBackedServiceValidatesClaimsWithPurposeScopedSecretBytes() {
        CredentialRegistry registry = tenantRegistry(SECRET);
        TenantAccessService service = new TenantAccessService(
                registry,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertDoesNotThrow(() -> service.require(
                signedHeaders(SECRET, NOW),
                TenantPermissions.JOB_READ
        ));
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

        assertEquals(
                "credential registry returned material for a different purpose",
                exception.getMessage()
        );
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

        assertEquals("tenant claims credential identity mismatch", exception.getMessage());
    }

    @Test
    void registryBackedServiceRejectsUndersizedCredential() {
        CredentialRegistry registry = tenantRegistry("t".repeat(31));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TenantAccessService(registry, 300L, Clock.fixed(NOW, ZoneOffset.UTC))
        );

        assertEquals("tenant claims credential requires at least 32 bytes", exception.getMessage());
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
        assertEquals(
                "credential registry returned no material",
                assertThrows(
                        IllegalStateException.class,
                        () -> new TenantAccessService(
                                reference -> null,
                                300L,
                                Clock.fixed(NOW, ZoneOffset.UTC)
                        )
                ).getMessage()
        );
        assertThrows(
                NullPointerException.class,
                () -> new TenantAccessService(
                        tenantRegistry(SECRET),
                        300L,
                        null
                )
        );
    }

    @Test
    void springManagedConstructorPrefersRegistryOverLegacyProperty() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("credentialRegistry", tenantRegistry(SECRET));
        ObjectProvider<CredentialRegistry> provider = beanFactory.getBeanProvider(CredentialRegistry.class);
        TenantAccessService service = new TenantAccessService(
                provider,
                "legacy-property-must-not-win",
                300L
        );
        Instant now = Instant.now();

        assertDoesNotThrow(() -> service.require(
                signedHeaders(SECRET, now),
                TenantPermissions.JOB_READ
        ));
    }

    @Test
    void springManagedConstructorUsesLegacyPropertyOnlyWithoutRegistry() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<CredentialRegistry> provider = beanFactory.getBeanProvider(CredentialRegistry.class);
        TenantAccessService service = new TenantAccessService(provider, SECRET, 300L);
        Instant now = Instant.now();

        assertDoesNotThrow(() -> service.require(
                signedHeaders(SECRET, now),
                TenantPermissions.JOB_READ
        ));
    }

    private static CredentialRegistry tenantRegistry(String secret) {
        return reference -> {
            assertEquals("tenant-claims-signing", reference.credentialName());
            assertEquals(CredentialPurpose.TENANT_CLAIMS_SIGNING, reference.purpose());
            return new CredentialSnapshot(
                    reference.credentialName(),
                    "v1",
                    CredentialPurpose.TENANT_CLAIMS_SIGNING,
                    secret.getBytes(StandardCharsets.UTF_8)
            );
        };
    }

    private static HttpHeaders signedHeaders(String secret, Instant issuedAt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.set(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);
        String issuedAtValue = String.valueOf(issuedAt.getEpochSecond());
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAtValue);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAtValue, secret)
        );
        return headers;
    }
}
