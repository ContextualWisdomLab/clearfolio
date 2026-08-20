package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RuntimeSecretBootstrapPolicyTest {

    private static final Path BASE_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path BUYER_DEMO_CONFIG =
            Path.of("src/main/resources/application-buyer-demo.yml");

    @Test
    void productionUsesOneBootstrapDirectoryWithoutRawSecretEnvironmentMappings()
            throws IOException {
        String baseConfig = Files.readString(BASE_CONFIG);
        String buyerDemoConfig = Files.readString(BUYER_DEMO_CONFIG);

        assertThat(baseConfig)
                .contains("optional:configtree:")
                .contains("CLEARFOLIO_SECRET_CONFIG_DIR")
                .contains("credential-registry:")
                .contains("root-directory: ${CLEARFOLIO_SECRET_CONFIG_DIR")
                .contains("tenant-claims-signing/");
        assertThat(baseConfig)
                .doesNotContain("CLEARFOLIO_ARTIFACT_TOKEN_SECRET")
                .doesNotContain("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET");
        assertThat(buyerDemoConfig)
                .doesNotContain("CLEARFOLIO_ARTIFACT_TOKEN_SECRET")
                .doesNotContain("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET")
                .doesNotContain("clearfolio.artifact-token.secret")
                .doesNotContain("clearfolio.tenant-claims.hmac-secret");
    }
}
