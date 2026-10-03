package com.clearfolio.viewer.config;

import java.util.Optional;

/**
 * Port for looking up runtime secrets and credentials from a KV store or environment bridge.
 */
public interface CredentialRegistryPort {

    /**
     * Retrieves a credential by name.
     *
     * @param name credential name
     * @return the credential value if present
     */
    Optional<String> getCredential(final String name);
}
