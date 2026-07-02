package com.clearfolio.viewer.analytics;

import java.time.Instant;

/**
 * Exported buyer KPI snapshot evidence.
 *
 * @param tenantId tenant that requested the snapshot
 * @param subjectId subject that requested the snapshot
 * @param exportedAt time when the snapshot was exported
 * @param totalJobs total jobs in the snapshot
 * @param submittedJobs submitted jobs in the snapshot
 * @param processingJobs processing jobs in the snapshot
 * @param succeededJobs succeeded jobs in the snapshot
 * @param failedJobs failed jobs in the snapshot
 * @param deadLetteredJobs dead-lettered jobs in the snapshot
 * @param conversionSuccessRate succeeded jobs divided by total jobs
 * @param p95TimeToPreviewMs p95 time to preview, when available
 */
public record KpiSnapshotRecord(
        String tenantId,
        String subjectId,
        Instant exportedAt,
        int totalJobs,
        int submittedJobs,
        int processingJobs,
        int succeededJobs,
        int failedJobs,
        int deadLetteredJobs,
        double conversionSuccessRate,
        Long p95TimeToPreviewMs
) {
}
