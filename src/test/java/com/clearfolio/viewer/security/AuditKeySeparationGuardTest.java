package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class AuditKeySeparationGuardTest {

    @Test
    void rejectsIdenticalConfiguredKeysDuringStartup() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("shared-key-material");
        properties.setAuditPseudonymSecret("shared-key-material");

        assertThrows(
                IllegalStateException.class,
                () -> new AuditKeySeparationGuard(properties)
        );
    }

    @Test
    void acceptsDistinctConfiguredKeys() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("policy-signing-key");
        properties.setAuditPseudonymSecret("audit-pseudonym-key");

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
