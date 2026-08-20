package com.clearfolio.viewer.credential;

/**
 * Cryptographic purpose that constrains how resolved credential material may be used.
 *
 * <p>Purpose separation prevents one signing key from silently becoming authority
 * for a different security boundary. Registry adapters must return material whose
 * stored purpose matches the requested reference.</p>
 */
public enum CredentialPurpose {

    /**
     * HMAC material used only for tenant-claim signing and verification.
     */
    TENANT_CLAIMS_SIGNING,

    /**
     * HMAC material used only for artifact-link signing and verification.
     */
    ARTIFACT_TOKEN_SIGNING
}
