package com.clearfolio.viewer.config;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Port for retrieving credentials from the runtime KV/config tree.
 * Replaces direct @Value environment injections for secrets.
 */
@Component
public class CredentialRegistryPort {

    /**
     * The environment.
     */
    private final Environment environment;

    /**
     * Creates the credential registry port.
     *
     * @param env the Spring environment
     */
    public CredentialRegistryPort(final Environment env) {
        this.environment = env;
    }

    /**
     * Retrieves a credential by name.
     *
     * @param name the credential property name
     * @return the credential value, or empty if not set
     */
    public Optional<String> getCredential(final String name) {
        return Optional.ofNullable(environment.getProperty(name));
    }
}
