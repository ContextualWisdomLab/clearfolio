package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class CredentialRegistryPortTest {

    @Test
    void retrievesConfiguredCredential() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("test.secret", "secret-value");
        CredentialRegistryPort port = new CredentialRegistryPort(env);

        assertThat(port.getCredential("test.secret")).hasValue("secret-value");
    }

    @Test
    void returnsEmptyForMissingCredential() {
        MockEnvironment env = new MockEnvironment();
        CredentialRegistryPort port = new CredentialRegistryPort(env);

        assertThat(port.getCredential("missing.secret")).isEmpty();
    }
}
