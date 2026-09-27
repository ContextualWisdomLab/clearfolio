package com.clearfolio.viewer.auth;

import java.util.Optional;

/**
 * Port for retrieving runtime credentials and secrets.
 */
@FunctionalInterface
public interface CredentialRegistryPort {

    String TENANT_CLAIMS_HMAC_SECRET = "clearfolio.tenant-claims.hmac-secret";
    String ARTIFACT_TOKEN_SECRET = "clearfolio.artifact-token.secret";

    /**
     * Resolves a credential by name.
     *
     * @param name credential name
     * @return credential value if present
     */
    Optional<String> getCredential(final String name);
}
