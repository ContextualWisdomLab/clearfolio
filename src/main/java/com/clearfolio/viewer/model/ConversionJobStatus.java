package com.clearfolio.viewer.model;

/**
 * Lifecycle states for a conversion job.
 */
public enum ConversionJobStatus {
    /** The service accepted and persisted the job but processing has not started. */
    SUBMITTED,
    /** A worker currently owns the processing lease and is attempting conversion. */
    PROCESSING,
    /** Conversion completed and the generated artifact is available. */
    SUCCEEDED,
    /** The latest conversion attempt failed, with retry metadata stored separately. */
    FAILED
}
