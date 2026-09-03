package com.clearfolio.viewer.security;

import java.util.Optional;

/**
 * Runtime port for resolving secrets from Clearfolio's credential registry.
 *
 * <p>Delivery adapters may receive secret material during bootstrap, but
 * application/runtime consumers resolve it through this port rather than
 * reading environment-backed Spring properties directly.</p>
 */
@FunctionalInterface
public interface CredentialRegistryPort {

    /** Registry key for the dedicated audit-pseudonym HMAC secret. */
    String AUDIT_PSEUDONYM_SECRET = "clearfolio.audit-pseudonym-secret";

    /** Registry key for tenant-claim HMAC verification material. */
    String TENANT_CLAIMS_HMAC_SECRET = "clearfolio.tenant-claims-hmac-secret";

    /**
     * Resolves one credential by canonical registry key.
     *
     * @param name canonical credential key
     * @return credential value when provisioned; otherwise empty
     */
    Optional<String> getCredential(String name);
}
