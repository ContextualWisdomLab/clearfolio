package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class EnvVarCredentialRegistryAdapterTest {

    @Test
    void getCredentialReturnsEmptyForUnknownName() {
        Environment env = mock(Environment.class);
        EnvVarCredentialRegistryAdapter adapter = new EnvVarCredentialRegistryAdapter(env);

        assertTrue(adapter.getCredential("unknown").isEmpty());
    }

    @Test
    void getCredentialResolvesArtifactTokenSecret() {
        Environment env = mock(Environment.class);
        when(env.getProperty("CLEARFOLIO_ARTIFACT_TOKEN_SECRET")).thenReturn("token-secret");
        EnvVarCredentialRegistryAdapter adapter = new EnvVarCredentialRegistryAdapter(env);

        Optional<String> secret = adapter.getCredential(CredentialRegistryPort.ARTIFACT_TOKEN_SECRET);

        assertTrue(secret.isPresent());
        assertEquals("token-secret", secret.get());
    }

    @Test
    void getCredentialResolvesTenantClaimsHmacSecret() {
        Environment env = mock(Environment.class);
        when(env.getProperty("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET")).thenReturn("tenant-secret");
        EnvVarCredentialRegistryAdapter adapter = new EnvVarCredentialRegistryAdapter(env);

        Optional<String> secret = adapter.getCredential(CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET);

        assertTrue(secret.isPresent());
        assertEquals("tenant-secret", secret.get());
    }
}
