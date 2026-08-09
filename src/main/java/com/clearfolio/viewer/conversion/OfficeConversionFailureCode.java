package com.clearfolio.viewer.conversion;

/**
 * Stable failure classes returned by qualified Office conversion adapters.
 *
 * <p>Retryability is part of the adapter contract so parser, policy, resource,
 * and user-input failures cannot be mistaken for transient engine failures.</p>
 */
public enum OfficeConversionFailureCode {

    /** Source format is outside the qualified support matrix. */
    UNSUPPORTED_FORMAT(false),
    /** Source was rejected by macro, active-content, or document policy. */
    POLICY_DENIED(false),
    /** Source requires a password and cannot be converted unattended. */
    PASSWORD_PROTECTED(false),
    /** Source structure is malformed or cannot be parsed safely. */
    MALFORMED_INPUT(false),
    /** Caller cancelled the exact conversion generation. */
    CANCELLED(false),
    /** Converter returned output that failed PDF validation. */
    INVALID_OUTPUT(false),
    /** Candidate PDF exceeds the request-bound publication size ceiling. */
    OUTPUT_LIMIT_EXCEEDED(false),
    /** Candidate PDF exceeds the request-bound publication page ceiling. */
    PAGE_LIMIT_EXCEEDED(false),
    /** Qualified converter service or capacity is temporarily unavailable. */
    ENGINE_UNAVAILABLE(true),
    /** Conversion exceeded its bounded execution deadline. */
    TIMEOUT(true),
    /** Isolated converter process or service crashed during execution. */
    ENGINE_CRASH(true);

    private final boolean retryable;

    OfficeConversionFailureCode(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Returns whether this failure class may enter bounded retry policy.
     *
     * @return {@code true} only for transient engine failure classes
     */
    public boolean isRetryable() {
        return retryable;
    }
}
