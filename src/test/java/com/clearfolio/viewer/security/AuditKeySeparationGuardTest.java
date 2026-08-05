package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class AuditKeySeparationGuardTest {

    private static final String POLICY_KEY = "0123456789abcdef0123456789abcdef";
    private static final String AUDIT_KEY = "fedcba9876543210fedcba9876543210";

    @Test
    void rejectsIdenticalConfiguredKeysDuringStartup() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret(POLICY_KEY);
        properties.setAuditPseudonymSecret(POLICY_KEY);

        assertThrows(
                IllegalStateException.class,
                () -> new AuditKeySeparationGuard(properties)
        );
    }

    @Test
    void rejectsConfiguredPolicyKeyShorterThanThirtyTwoUtf8Bytes() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("short-policy-key");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new AuditKeySeparationGuard(properties)
        );

        assertTrue(exception.getMessage().contains("at least 32 UTF-8 bytes"));
    }

    @Test
    void acceptsDistinctConfiguredKeys() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret(POLICY_KEY);
        properties.setAuditPseudonymSecret(AUDIT_KEY);

        assertDoesNotThrow(() -> new AuditKeySeparationGuard(properties));
    }

    @Test
    void acceptsConfiguredPolicyKeyMeasuredAsThirtyTwoOrMoreUtf8Bytes() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("가나다라마바사아자차카");

        assertDoesNotThrow(() -> new AuditKeySeparationGuard(properties));
    }

    @Test
    void permitsDisabledSecurityPurposesWithoutComparingMissingValues() {
        assertDoesNotThrow(() -> AuditKeySeparationGuard.requireDistinct(null, "audit-key"));
        assertDoesNotThrow(() -> AuditKeySeparationGuard.requireDistinct("policy-key", null));
        assertDoesNotThrow(() -> AuditKeySeparationGuard.requireDistinct(" ", "audit-key"));
        assertDoesNotThrow(() -> AuditKeySeparationGuard.requireDistinct("policy-key", " "));
    }
}
