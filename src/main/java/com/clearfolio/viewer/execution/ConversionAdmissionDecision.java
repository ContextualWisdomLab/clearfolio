package com.clearfolio.viewer.execution;

/**
 * Outcome categories for one conversion admission attempt.
 *
 * <p>An accepted decision means the service has established authority for a
 * canonical job identity. A capacity rejection means no job was accepted and
 * callers may retry according to separately bounded guidance.</p>
 */
public enum ConversionAdmissionDecision {
    /** A new canonical conversion job was accepted. */
    ACCEPTED,
    /** The request resolved to an already accepted canonical job. */
    IDEMPOTENT_REPLAY,
    /** Capacity prevented admission, so no conversion job was accepted. */
    CAPACITY_REJECTED
}
