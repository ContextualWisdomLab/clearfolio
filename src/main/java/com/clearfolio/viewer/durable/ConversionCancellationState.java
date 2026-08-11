package com.clearfolio.viewer.durable;

/**
 * Monotonic durable cancellation outcome for one conversion-job generation.
 *
 * <p>The state starts at {@link #REQUESTED}. Exactly one terminal winner may be
 * recorded: either the cancellation succeeds, or completion wins the race
 * before cancellation becomes effective. A terminal outcome must never be
 * rewritten by a later stale acknowledgement.</p>
 */
public enum ConversionCancellationState {

    /**
     * Cancellation was requested and no terminal winner has been recorded.
     */
    REQUESTED,

    /**
     * Cancellation won the race for the bound job generation.
     */
    CANCELLED,

    /**
     * Job completion won before cancellation became effective.
     */
    COMPLETED_BEFORE_CANCELLATION
}
