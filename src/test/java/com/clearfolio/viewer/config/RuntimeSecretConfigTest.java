package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;

class RuntimeSecretConfigTest {

    @Test
    void baseConfigProvidesNonSecretFallbacksForLocalAndDemoStartup() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(properties.getProperty("clearfolio.artifact-token.secret")).isEqualTo("");
        assertThat(properties.getProperty("clearfolio.tenant-claims.hmac-secret")).isEqualTo("");
        assertThat(properties.getProperty("spring.config.import"))
                .asString()
                .startsWith("optional:configtree:");
    }
}
