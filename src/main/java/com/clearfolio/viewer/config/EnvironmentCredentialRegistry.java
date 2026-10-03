package com.clearfolio.viewer.config;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Spring Environment bridge implementation of CredentialRegistryPort.
 */
@Component
public class EnvironmentCredentialRegistry implements CredentialRegistryPort {

    private final Environment environment;

    /**
     * Creates an EnvironmentCredentialRegistry.
     *
     * @param environment Spring Environment
     */
    public EnvironmentCredentialRegistry(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getCredential(final String name) {
        return Optional.ofNullable(environment.getProperty(name));
    }
}
