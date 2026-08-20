package com.clearfolio.viewer.auth;

/**
 * Provider-neutral boundary that verifies one untrusted identity credential.
 *
 * <p>The verifier profile is server-owned configuration that selects an approved
 * issuer/introspection policy. The bearer token is opaque input and must not be
 * logged, normalized, copied into exceptions, or used to derive accepted
 * algorithms or issuer endpoints. Implementations return only a verified
 * {@link TenantContext} or fail closed with {@link IdentityVerificationException}.
 * This keeps Clearfolio authorization independent of a specific OIDC, JWT, or
 * opaque-token provider.</p>
 */
@FunctionalInterface
public interface IdentityVerifier {

    /**
     * Version of the provider-neutral verification contract.
     */
    String CONTRACT_VERSION = "identity-verifier-v1";

    /**
     * Verifies one bearer credential under an explicitly selected trust profile.
     *
     * @param profileId server-owned verifier/trust-policy identifier
     * @param bearerToken opaque untrusted bearer credential
     * @return immutable verified tenant and permission context; never {@code null}
     * @throws IdentityVerificationException when the credential is rejected or
     *         the configured verification authority is temporarily unavailable
     */
    TenantContext verify(String profileId, String bearerToken);
}
