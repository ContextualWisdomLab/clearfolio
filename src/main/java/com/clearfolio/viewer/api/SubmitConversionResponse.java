package com.clearfolio.viewer.api;

import java.util.UUID;

/**
 * API payload returned after Clearfolio durably accepts a conversion request
 * for asynchronous processing.
 *
 * @param jobId stable identifier used for status, viewer, and artifact requests
 * @param status initial lifecycle state exposed to the client
 * @param statusUrl same-origin URL from which the client can poll job state
 */
public record SubmitConversionResponse(UUID jobId, String status, String statusUrl) {

    /**
     * Builds the standard accepted response for a new conversion job.
     *
     * @param jobId accepted conversion job identifier
     * @return accepted conversion response payload
     */
    public static SubmitConversionResponse accepted(UUID jobId) {
        return new SubmitConversionResponse(jobId, "ACCEPTED", "/api/v1/convert/jobs/" + jobId);
    }
}
