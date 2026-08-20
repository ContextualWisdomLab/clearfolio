package com.clearfolio.viewer.durable;

/**
 * Controlled lifecycle vocabulary for a conversion execution audit event.
 */
public enum ConversionExecutionEventType {
    /** A worker claimed the execution attempt. */
    CLAIMED,

    /** The execution failed but may be retried. */
    RETRYABLE_FAILED,

    /** The execution was accepted for durable processing. */
    ACCEPTED,

    /** The execution completed successfully. */
    SUCCEEDED,

    /** The execution failed without another retry. */
    TERMINAL_FAILED
}
