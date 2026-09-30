package com.clearfolio.viewer.auth;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Registry adapter that bridges environment variables to the credential port.
 */
@Component
public class EnvironmentCredentialRegistryAdapter implements CredentialRegistryPort {

    private final Environment environment;

    /**
     * Creates an adapter using the given Spring environment.
     *
     * @param environment Spring environment
     */
    public EnvironmentCredentialRegistryAdapter(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getCredential(final String name) {
        return Optional.ofNullable(environment.getProperty(name));
    }
}
