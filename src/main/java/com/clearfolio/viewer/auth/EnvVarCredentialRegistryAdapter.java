package com.clearfolio.viewer.auth;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

/**
 * Bridge adapter to resolve credentials from the environment.
 */
@Component
public class EnvVarCredentialRegistryAdapter implements CredentialRegistryPort {

    private final Environment environment;

    /**
     * Creates an adapter using the Spring Environment.
     *
     * @param environment Spring environment
     */
    public EnvVarCredentialRegistryAdapter(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getCredential(final String name) {
        if (ARTIFACT_TOKEN_SECRET.equals(name)) {
            return Optional.ofNullable(environment.getProperty("CLEARFOLIO_ARTIFACT_TOKEN_SECRET"));
        }
        if (TENANT_CLAIMS_HMAC_SECRET.equals(name)) {
            return Optional.ofNullable(environment.getProperty("CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET"));
        }
        return Optional.empty();
    }
}
