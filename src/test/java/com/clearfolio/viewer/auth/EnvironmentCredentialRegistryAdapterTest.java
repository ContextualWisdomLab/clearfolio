package com.clearfolio.viewer.auth;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentCredentialRegistryAdapterTest {

    @Test
    void getCredentialReturnsValueWhenPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("my.secret", "foo");

        EnvironmentCredentialRegistryAdapter adapter = new EnvironmentCredentialRegistryAdapter(env);

        Optional<String> result = adapter.getCredential("my.secret");

        assertTrue(result.isPresent());
        assertEquals("foo", result.get());
    }

    @Test
    void getCredentialReturnsEmptyWhenAbsent() {
        MockEnvironment env = new MockEnvironment();

        EnvironmentCredentialRegistryAdapter adapter = new EnvironmentCredentialRegistryAdapter(env);

        Optional<String> result = adapter.getCredential("missing.secret");

        assertTrue(result.isEmpty());
    }
}
