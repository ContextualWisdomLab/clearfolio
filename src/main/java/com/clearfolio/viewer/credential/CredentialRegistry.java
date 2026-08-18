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
 * <p>Runtime consumers should call {@link #resolveScoped(CredentialReference)}.
 * The single abstract {@link #resolve(CredentialReference)} method remains the
 * adapter hook so registry implementations stay lambda-friendly while the
 * contract boundary independently verifies purpose separation.</p>
 */
@FunctionalInterface
public interface CredentialRegistry {

    /**
     * Version of the provider-neutral credential resolution contract.
     */
    String CONTRACT_VERSION = "credential-registry-v1";

    /**
     * Resolves one credential reference through the provider adapter.
     *
     * <p>This is the implementation hook. Runtime consumers should use
     * {@link #resolveScoped(CredentialReference)} so an adapter cannot silently
     * return material authorized for a different cryptographic purpose.</p>
     *
     * @param reference server-owned logical credential reference
     * @return versioned credential snapshot supplied by the adapter
     */
    CredentialSnapshot resolve(CredentialReference reference);

    /**
     * Resolves credential material and fails closed when adapter authority drifts.
     *
     * @param reference server-owned logical credential reference
     * @return versioned credential snapshot whose purpose matches the reference
     * @throws NullPointerException when the reference is absent
     * @throws IllegalStateException when the adapter returns no material or returns
     *                               material authorized for a different purpose
     */
    default CredentialSnapshot resolveScoped(CredentialReference reference) {
        CredentialReference requiredReference = Objects.requireNonNull(reference, "reference");
        CredentialSnapshot snapshot = resolve(requiredReference);
        if (snapshot == null) {
            throw new IllegalStateException("credential registry returned no material");
        }
        if (snapshot.purpose() != requiredReference.purpose()) {
            throw new IllegalStateException("credential registry returned material for a different purpose");
        }
        return snapshot;
    }
}
