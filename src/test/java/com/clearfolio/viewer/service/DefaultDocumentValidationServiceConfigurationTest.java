package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class DefaultDocumentValidationServiceConfigurationTest {

    @Test
    void rejectsEnabledPolicyOverrideWithoutDedicatedAuditKey() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("0123456789abcdef0123456789abcdef");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new DefaultDocumentValidationService(properties)
        );

        assertTrue(exception.getMessage().contains("audit pseudonym key is required"));
    }
}
