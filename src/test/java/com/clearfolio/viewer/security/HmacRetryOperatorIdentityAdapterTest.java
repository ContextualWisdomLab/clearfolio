package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class HmacRetryOperatorIdentityAdapterTest {

    private static final String AUDIT_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void usesCredentialRegistryForDedicatedVersionedDomainSeparatedAuditFingerprint() {
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymKeyVersion("v7");
        HmacRetryOperatorIdentityAdapter adapter = new HmacRetryOperatorIdentityAdapter(
                credentialRegistry(AUDIT_KEY),
                properties
        );

        String retryIdentity = adapter.pseudonymize("user-1");
        String approverIdentity = new AuditPseudonymizer(AUDIT_KEY, "v7").fingerprint("user-1");

        assertTrue(retryIdentity.startsWith("v7:"));
        assertNotEquals(approverIdentity, retryIdentity);
        assertNotEquals(
                "b32817bf034f5dcb3ac5f1e8dc3a19fc82dd24409bb10bc1a1f0a2dbb059f131",
                retryIdentity
        );
    }

    @Test
    void returnsSafeUnavailableMarkerWhenRegistryHasNoAuditCorrelationKey() {
        ConversionProperties properties = new ConversionProperties();
        HmacRetryOperatorIdentityAdapter adapter = new HmacRetryOperatorIdentityAdapter(
                credentialRegistry(null),
                properties
        );

        assertTrue(adapter.pseudonymize("user-1").startsWith("unavailable:"));
    }

    private static CredentialRegistryPort credentialRegistry(String auditKey) {
        return name -> CredentialRegistryPort.AUDIT_PSEUDONYM_SECRET.equals(name)
                ? Optional.ofNullable(auditKey)
                : Optional.empty();
    }
}
