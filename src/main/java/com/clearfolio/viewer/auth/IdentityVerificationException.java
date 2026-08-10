package com.clearfolio.viewer.auth;

import java.io.Serial;
import java.util.Objects;

/**
 * Controlled failure returned by a provider-neutral identity verifier.
 *
 * <p>The exception deliberately carries only a low-cardinality failure kind and
 * fixed message. Provider responses, bearer tokens, claims, issuer values, and
 * tenant or subject identifiers must not be copied into this exception.</p>
 */
public final class IdentityVerificationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Controlled verifier outcomes safe for authorization routing and telemetry.
     */
    public enum FailureKind {
        /**
         * The presented credential or mapped identity did not satisfy policy.
         */
        REJECTED,

        /**
         * The configured verification authority could not complete verification.
         */
        UNAVAILABLE
    }

    private final FailureKind failureKind;

    /**
     * Creates a controlled identity-verification failure.
     *
     * @param failureKind low-cardinality rejection or availability category
     * @throws NullPointerException when the failure kind is absent
     */
    public IdentityVerificationException(FailureKind failureKind) {
        super(message(failureKind));
        this.failureKind = failureKind;
    }

    /**
     * Returns the controlled verifier failure category.
     *
     * @return rejection or availability category
     */
    public FailureKind failureKind() {
        return failureKind;
    }

    private static String message(FailureKind failureKind) {
        Objects.requireNonNull(failureKind, "failureKind");
        if (failureKind == FailureKind.REJECTED) {
            return "identity verification rejected";
        }
        return "identity verification unavailable";
    }
}
