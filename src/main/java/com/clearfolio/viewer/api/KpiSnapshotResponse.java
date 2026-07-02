package com.clearfolio.viewer.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * API payload for buyer-facing conversion KPI snapshots.
 *
 * @param totalJobs total jobs currently known to the runtime
 * @param submittedJobs jobs waiting for processing
 * @param processingJobs jobs currently processing
 * @param succeededJobs jobs with a ready preview artifact
 * @param failedJobs jobs in failed state
 * @param deadLetteredJobs failed jobs that exhausted retry handling
 * @param conversionSuccessRate succeeded jobs divided by total jobs
 * @param p95TimeToPreviewMs p95 processing time for succeeded jobs with timestamps
 */
public record KpiSnapshotResponse(
        int totalJobs,
        int submittedJobs,
        int processingJobs,
        int succeededJobs,
        int failedJobs,
        int deadLetteredJobs,
        double conversionSuccessRate,
        Long p95TimeToPreviewMs
) {

    /**
     * Builds a KPI snapshot from current conversion jobs.
     *
     * @param jobs current conversion jobs
     * @return KPI snapshot response
     */
    public static KpiSnapshotResponse from(List<ConversionJob> jobs) {
        int submitted = 0;
        int processing = 0;
        int succeeded = 0;
        int failed = 0;
        int deadLettered = 0;
        List<Long> previewDurations = new ArrayList<>();

        for (ConversionJob job : jobs) {
            ConversionJobStatus status = job.getStatus();
            if (status == ConversionJobStatus.SUBMITTED) {
                submitted++;
            } else if (status == ConversionJobStatus.PROCESSING) {
                processing++;
            } else if (status == ConversionJobStatus.SUCCEEDED) {
                succeeded++;
                addPreviewDuration(previewDurations, job);
            } else {
                failed++;
            }

            if (job.isDeadLettered()) {
                deadLettered++;
            }
        }

        int total = jobs.size();
        double successRate = total == 0 ? 0.0 : (double) succeeded / total;
        return new KpiSnapshotResponse(
                total,
                submitted,
                processing,
                succeeded,
                failed,
                deadLettered,
                successRate,
                p95(previewDurations)
        );
    }

    private static void addPreviewDuration(List<Long> durations, ConversionJob job) {
        if (job.getStartedAt() == null || job.getCompletedAt() == null) {
            return;
        }

        durations.add(Math.max(0L, Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis()));
    }

    private static Long p95(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }

        values.sort(Comparator.naturalOrder());
        int index = Math.max(0, (int) Math.ceil(values.size() * 0.95) - 1);
        return values.get(index);
    }
}
