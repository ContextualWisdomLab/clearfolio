package com.clearfolio.viewer.credential;

import java.util.Objects;

/**
 * Provider-neutral boundary for resolving versioned credential material.
 *
 * <p>Implementations may use an encrypted database registry, managed secret
 * service, HSM/KMS-backed adapter, or deterministic test fixture. Runtime
 * consumers depend only on this contract so environment variables can remain
 * bootstrap transport rather than the credential source of truth.</p>
 *
 * <p>Runtime consumers call {@link #resolveScoped(CredentialReference)}. The
 * single abstract {@link #resolveAdapterMaterial(CredentialReference)} method is
 * deliberately named as an adapter hook so the lambda-friendly implementation
 * surface cannot be mistaken for the authority-enforcing consumer boundary.</p>
 */
@FunctionalInterface
public interface CredentialRegistry {

    /**
     * Version of the provider-neutral credential resolution contract.
     */
    String CONTRACT_VERSION = "credential-registry-v1";

    /**
     * Obtains one credential snapshot from a provider adapter.
     *
     * <p>This method is an adapter implementation hook only. Runtime consumers
     * must use {@link #resolveScoped(CredentialReference)}, which independently
     * verifies that returned material is present and bound to the requested
     * logical credential identity and cryptographic purpose.</p>
     *
     * @param reference server-owned logical credential reference
     * @return versioned credential snapshot supplied by the adapter
     */
    CredentialSnapshot resolveAdapterMaterial(CredentialReference reference);

    /**
     * Resolves credential material and fails closed when adapter authority drifts.
     *
     * @param reference server-owned logical credential reference
     * @return versioned credential snapshot whose identity and purpose match the reference
     * @throws NullPointerException when the reference is absent
     * @throws IllegalStateException when the adapter returns no material or returns
     *                               material for a different logical identity or purpose
     */
    default CredentialSnapshot resolveScoped(CredentialReference reference) {
        CredentialReference requiredReference = Objects.requireNonNull(reference, "reference");
        CredentialSnapshot snapshot = resolveAdapterMaterial(requiredReference);
        if (snapshot == null) {
            throw new IllegalStateException("credential registry returned no material");
        }
        if (!snapshot.credentialId().equals(requiredReference.credentialName())) {
            throw new IllegalStateException("credential registry returned material for a different identity");
        }
        if (snapshot.purpose() != requiredReference.purpose()) {
            throw new IllegalStateException("credential registry returned material for a different purpose");
        }
        return snapshot;
    }
}
