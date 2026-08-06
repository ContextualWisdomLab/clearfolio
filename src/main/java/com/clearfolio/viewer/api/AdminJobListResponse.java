package com.clearfolio.viewer.api;

import java.util.List;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Payload representing a list of conversion jobs for admin view.
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
