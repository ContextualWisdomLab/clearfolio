package com.clearfolio.viewer.credential;

import java.util.Objects;

/**
 * Immutable server-owned reference to one purpose-scoped credential.
 *
 * @param credentialName stable logical credential name, never secret material
 * @param purpose cryptographic purpose that the resolved material may serve
 */
public record CredentialReference(String credentialName, CredentialPurpose purpose) {

    /**
     * Validates and normalizes the credential authority reference.
     *
     * @throws IllegalArgumentException when the credential name is absent or blank
     * @throws NullPointerException when the credential purpose is absent
     */
    public CredentialReference {
        if (credentialName == null || credentialName.isBlank()) {
            throw new IllegalArgumentException("credentialName must not be blank");
        }
        credentialName = credentialName.strip();
        Objects.requireNonNull(purpose, "purpose");
    }
}
