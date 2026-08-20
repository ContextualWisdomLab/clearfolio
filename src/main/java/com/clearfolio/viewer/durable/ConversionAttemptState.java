package com.clearfolio.viewer.durable;

/**
 * Durable lifecycle state for one conversion execution attempt.
 *
 * <p>An attempt begins in {@link #CLAIMED}. Every other value is terminal for
 * that exact attempt identity; retries use a new attempt record rather than
 * reopening the completed one.</p>
 */
public enum ConversionAttemptState {
    /** Worker authority has been claimed and no terminal outcome is recorded. */
    CLAIMED,

    /** The attempt completed and produced the authorized successful result. */
    SUCCEEDED,

    /** The attempt failed in a way that may be retried by a separate schedule record. */
    RETRYABLE_FAILED,

    /** The attempt failed terminally and must not be retried automatically. */
    TERMINAL_FAILED
}
