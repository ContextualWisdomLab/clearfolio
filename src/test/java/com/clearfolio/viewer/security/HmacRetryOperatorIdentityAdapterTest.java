package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class HmacRetryOperatorIdentityAdapterTest {

    private static final String AUDIT_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void usesDedicatedVersionedDomainSeparatedAuditFingerprint() {
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret(AUDIT_KEY);
        properties.setAuditPseudonymKeyVersion("v7");
        HmacRetryOperatorIdentityAdapter adapter = new HmacRetryOperatorIdentityAdapter(properties);

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
    void returnsSafeUnavailableMarkerWhenAuditCorrelationKeyIsDisabled() {
        ConversionProperties properties = new ConversionProperties();
        HmacRetryOperatorIdentityAdapter adapter = new HmacRetryOperatorIdentityAdapter(properties);

        assertTrue(adapter.pseudonymize("user-1").startsWith("unavailable:"));
    }
}
