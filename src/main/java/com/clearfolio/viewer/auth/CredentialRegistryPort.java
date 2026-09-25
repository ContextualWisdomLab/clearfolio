package com.clearfolio.viewer.auth;

import java.util.Optional;

/**
 * Port for retrieving runtime credentials and secrets from a key vault.
 */
@FunctionalInterface
public interface CredentialRegistryPort {

    /**
     * Registry key for the artifact token signing secret.
     */
    String ARTIFACT_TOKEN_SECRET = "clearfolio.artifact-token.secret";

    /**
     * Registry key for the tenant claims HMAC secret.
     */
    String TENANT_CLAIMS_HMAC_SECRET = "clearfolio.tenant-claims.hmac-secret";

    /**
     * Resolves a named credential from the registry.
     *
     * @param name credential key
     * @return resolved credential, or empty if not found
     */
    Optional<String> getCredential(final String name);
}
