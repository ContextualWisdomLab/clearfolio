package com.clearfolio.viewer.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral temporal authority extracted from verified federated token
 * claims.
 *
 * <p>The optional not-before instant models standards-compatible tokens that do
 * not carry an {@code nbf} claim. Expiration remains mandatory at this boundary.
 * Validation receives an explicit clock-skew allowance from server-owned policy
 * rather than deriving tolerance from untrusted token input.</p>
 */
public final class FederatedTokenTimeWindow {

    private final Instant notBefore;
    private final Instant expiresAt;

    /**
     * Creates a normalized federated-token validity window.
     *
     * @param notBefore optional inclusive lower validity bound
     * @param expiresAt required exclusive expiration instant
     * @throws NullPointerException when {@code expiresAt} is null
     * @throws IllegalArgumentException when the lower bound is not before expiration
     */
    public FederatedTokenTimeWindow(Instant notBefore, Instant expiresAt) {
        Instant requiredExpiry = Objects.requireNonNull(expiresAt, "expiresAt");
        if (notBefore != null && !notBefore.isBefore(requiredExpiry)) {
            throw new IllegalArgumentException("notBefore must precede expiresAt");
        }
        this.notBefore = notBefore;
        this.expiresAt = requiredExpiry;
    }

    /**
     * Checks whether the token window is active at an exact verifier time.
     *
     * <p>The not-before boundary is inclusive after applying skew. Expiration is
     * exclusive: with zero skew, a token is invalid exactly at {@code expiresAt}.
     * An absent not-before claim does not cause this value object to invent a
     * lower validity bound.</p>
     *
     * @param now verifier-owned current time
     * @param allowedClockSkew non-negative tolerance supplied by trusted policy
     * @return true when the current time falls inside the skew-adjusted window
     * @throws NullPointerException when an input is null
     * @throws IllegalArgumentException when clock skew is negative
     */
    public boolean isActiveAt(Instant now, Duration allowedClockSkew) {
        Instant requiredNow = Objects.requireNonNull(now, "now");
        Duration requiredSkew = Objects.requireNonNull(allowedClockSkew, "allowedClockSkew");
        if (requiredSkew.isNegative()) {
            throw new IllegalArgumentException("allowedClockSkew must not be negative");
        }

        if (notBefore != null
                && Duration.between(requiredNow, notBefore).compareTo(requiredSkew) > 0) {
            return false;
        }

        return Duration.between(expiresAt, requiredNow).compareTo(requiredSkew) < 0;
    }

    /**
     * Returns the optional inclusive not-before bound.
     *
     * @return not-before instant, or null when the normalized token has no lower bound
     */
    public Instant notBefore() {
        return notBefore;
    }

    /**
     * Returns the required exclusive expiration instant.
     *
     * @return expiration instant
     */
    public Instant expiresAt() {
        return expiresAt;
    }
}
