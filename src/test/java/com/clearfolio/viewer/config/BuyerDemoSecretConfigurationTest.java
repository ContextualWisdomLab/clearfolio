package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Verifies that buyer-demo runtime signing keys come only from the shared
 * config-tree mount rather than direct environment or profile literals.
 */
class BuyerDemoSecretConfigurationTest {

    @Test
    void signingSecretsComeFromTheSharedConfigTree() throws IOException {
        String baseConfiguration = readResource("application.yml");
        String buyerDemoConfiguration = readResource("application-buyer-demo.yml");

        assertTrue(baseConfiguration.contains(
                "optional:configtree:${CLEARFOLIO_SECRET_CONFIG_DIR:/run/secrets/clearfolio/}"
        ));
        assertTrue(baseConfiguration.contains("clearfolio.artifact-token.secret"));
        assertTrue(baseConfiguration.contains("clearfolio.tenant-claims.hmac-secret"));
        assertFalse(buyerDemoConfiguration.contains("CLEARFOLIO_ARTIFACT_TOKEN_SECRET"));
        assertFalse(buyerDemoConfiguration.contains("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET"));
        assertFalse(buyerDemoConfiguration.contains("artifact-token:"));
        assertFalse(buyerDemoConfiguration.contains("hmac-secret:"));
    }

    private static String readResource(String name) throws IOException {
        return new ClassPathResource(name)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
