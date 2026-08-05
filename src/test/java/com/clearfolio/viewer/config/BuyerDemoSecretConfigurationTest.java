package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class BuyerDemoSecretConfigurationTest {

    @Test
    void tenantClaimsSecretComesFromTheSharedConfigTree() throws IOException {
        String baseConfiguration = readResource("application.yml");
        String buyerDemoConfiguration = readResource("application-buyer-demo.yml");

        assertTrue(baseConfiguration.contains(
                "optional:configtree:${CLEARFOLIO_SECRET_CONFIG_DIR:/run/secrets/clearfolio/}"
        ));
        assertFalse(buyerDemoConfiguration.contains("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET"));
        assertFalse(buyerDemoConfiguration.contains("hmac-secret: ${"));
    }

    private static String readResource(String name) throws IOException {
        return new ClassPathResource(name)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
