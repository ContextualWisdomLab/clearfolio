package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Covers the fail-safe normalization contract for optional policy secrets.
 */
class ConversionPropertiesCoverageTest {

    @Test
    void nullPolicyOverrideSecretDisablesTheOverrideInsteadOfStoringNull() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("configured-secret");

        properties.setPolicyOverrideSecret(null);

        assertEquals("", properties.getPolicyOverrideSecret());
    }
}
