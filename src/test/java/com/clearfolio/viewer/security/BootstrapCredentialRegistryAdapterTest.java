package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;

class BootstrapCredentialRegistryAdapterTest {

    @Test
    void bootstrapsAndSanitizesProvisionedCredentials() {
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret("  audit-secret-012345678901234567890123  ");
        BootstrapCredentialRegistryAdapter registry = new BootstrapCredentialRegistryAdapter(
                properties,
                "\u0000 tenant-secret "
        );

        assertEquals(
                "audit-secret-012345678901234567890123",
                registry.getCredential(CredentialRegistryPort.AUDIT_PSEUDONYM_SECRET).orElseThrow()
        );
        assertEquals(
                "tenant-secret",
                registry.getCredential(CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET).orElseThrow()
        );
    }

    @Test
    void omitsMissingBlankAndUnknownCredentials() {
        ConversionProperties properties = new ConversionProperties();
        BootstrapCredentialRegistryAdapter registry = new BootstrapCredentialRegistryAdapter(
                properties,
                null
        );

        assertTrue(registry.getCredential(CredentialRegistryPort.AUDIT_PSEUDONYM_SECRET).isEmpty());
        assertTrue(registry.getCredential(CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET).isEmpty());
        assertTrue(registry.getCredential("unknown").isEmpty());
        assertTrue(registry.getCredential(null).isEmpty());
    }
}
