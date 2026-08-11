package com.clearfolio.viewer.durable;

/**
 * Controlled lifecycle vocabulary for privacy-safe conversion execution audit evidence.
 *
 * <p>The values identify bounded execution milestones without carrying document,
 * tenant, subject, token, exception, or other uncontrolled high-cardinality data.</p>
 */
public enum ConversionExecutionEventType {
    /** A conversion job was durably accepted by the execution boundary. */
    ACCEPTED,

    /** A worker claimed authority for one conversion execution attempt. */
    CLAIMED,

    /** The conversion attempt completed successfully. */
    SUCCEEDED,

    /** The conversion attempt reached a terminal failure. */
    TERMINAL_FAILED
}
