package com.clearfolio.viewer.credential;

import java.util.Objects;

/**
 * Immutable versioned snapshot of secret material returned by a credential registry.
 *
 * <p>Secret bytes are defensively copied on construction and access. The string
 * representation is deliberately redacted so routine logging cannot reveal key
 * material.</p>
 */
public final class CredentialSnapshot {

    /** Stable logical identifier for the resolved credential. */
    private final String credentialId;

    /** Version identifier of the resolved credential material. */
    private final String version;

    /** Cryptographic purpose authorized for the resolved material. */
    private final CredentialPurpose purpose;

    /** Private defensive copy of the resolved secret bytes. */
    private final byte[] secretBytes;

    /**
     * Creates one immutable credential snapshot.
     *
     * @param credentialId stable logical credential identifier
     * @param version exact resolved credential version
     * @param purpose cryptographic purpose authorized for this material
     * @param secretBytes non-empty secret material copied by this instance
     * @throws IllegalArgumentException when an identifier, version, or secret is absent
     * @throws NullPointerException when the credential purpose is absent
     */
    public CredentialSnapshot(
            String credentialId,
            String version,
            CredentialPurpose purpose,
            byte[] secretBytes
    ) {
        this.credentialId = requireText(credentialId, "credentialId");
        this.version = requireText(version, "version");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        if (secretBytes == null || secretBytes.length == 0) {
            throw new IllegalArgumentException("secretBytes must not be empty");
        }
        this.secretBytes = secretBytes.clone();
    }

    /**
     * Returns the stable logical credential identifier.
     *
     * @return credential identifier
     */
    public String credentialId() {
        return credentialId;
    }

    /**
     * Returns the exact resolved credential version.
     *
     * @return credential version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the cryptographic purpose authorized for this material.
     *
     * @return credential purpose
     */
    public CredentialPurpose purpose() {
        return purpose;
    }

    /**
     * Returns a defensive copy of the secret material.
     *
     * @return newly copied secret bytes
     */
    public byte[] secretBytes() {
        return secretBytes.clone();
    }

    /**
     * Returns metadata while redacting the secret payload.
     *
     * @return redacted snapshot description
     */
    @Override
    public String toString() {
        return "CredentialSnapshot[credentialId=" + credentialId
                + ", version=" + version
                + ", purpose=" + purpose
                + ", secret=<redacted>]";
    }

    /**
     * Validates and strips a public metadata field.
     *
     * @param value metadata value
     * @param fieldName field name used only in the controlled error message
     * @return stripped non-blank value
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
