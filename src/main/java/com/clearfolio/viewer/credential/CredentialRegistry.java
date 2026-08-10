package com.clearfolio.viewer.credential;

/**
 * Provider-neutral boundary for resolving versioned credential material.
 *
 * <p>Implementations may use an encrypted database registry, managed secret
 * service, HSM/KMS-backed adapter, or deterministic test fixture. Runtime
 * consumers depend only on this contract so environment variables can remain
 * bootstrap transport rather than the credential source of truth.</p>
 */
@FunctionalInterface
public interface CredentialRegistry {

    /**
     * Version of the provider-neutral credential resolution contract.
     */
    String CONTRACT_VERSION = "credential-registry-v1";

    /**
     * Resolves one purpose-scoped credential reference.
     *
     * @param reference server-owned logical credential reference
     * @return versioned credential snapshot whose purpose matches the reference
     */
    CredentialSnapshot resolve(CredentialReference reference);
}
