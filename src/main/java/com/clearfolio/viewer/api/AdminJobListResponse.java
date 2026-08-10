package com.clearfolio.viewer.api;

import java.util.List;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Administrative API payload containing conversion jobs that already passed
 * the caller's authorization and tenant-scope checks.
 *
 * @param jobs ordered job summaries safe to return to the authorized caller
 */
public record AdminJobListResponse(
        List<ConversionJobStatusResponse> jobs
) {
    /**
     * Creates an admin job list response from an iterable of jobs.
     *
     * @param jobs iterable of conversion jobs
     * @return admin job list response
     */
    public static AdminJobListResponse from(Iterable<ConversionJob> jobs) {
        java.util.List<ConversionJobStatusResponse> list = new java.util.ArrayList<>();
        for (ConversionJob job : jobs) {
            list.add(ConversionJobStatusResponse.from(job));
        }
        return new AdminJobListResponse(list);
    }
}
