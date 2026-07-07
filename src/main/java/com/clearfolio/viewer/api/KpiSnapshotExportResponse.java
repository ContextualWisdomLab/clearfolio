package com.clearfolio.viewer.api;

import java.time.Instant;

import com.clearfolio.viewer.analytics.KpiSnapshotRecord;

/**
 * API payload for exported KPI snapshot evidence.
 *
 * @param subjectId subject that exported the KPI snapshot
 * @param exportedAt time when the snapshot was exported
 * @param totalJobs total jobs in the exported snapshot
 * @param submittedJobs submitted jobs in the exported snapshot
 * @param processingJobs processing jobs in the exported snapshot
 * @param succeededJobs succeeded jobs in the exported snapshot
 * @param failedJobs failed jobs in the exported snapshot
 * @param deadLetteredJobs dead-lettered jobs in the exported snapshot
 * @param conversionSuccessRate succeeded jobs divided by total jobs
 * @param p95TimeToPreviewMs p95 time to preview, when available
 */
public record KpiSnapshotExportResponse(
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

    /**
     * Creates a tenant-scoped API response from ledger evidence.
     *
     * @param record persisted snapshot evidence
     * @return API response without repeating the tenant id
     */
    public static KpiSnapshotExportResponse from(KpiSnapshotRecord record) {
        return new KpiSnapshotExportResponse(
                record.subjectId(),
                record.exportedAt(),
                record.totalJobs(),
                record.submittedJobs(),
                record.processingJobs(),
                record.succeededJobs(),
                record.failedJobs(),
                record.deadLetteredJobs(),
                record.conversionSuccessRate(),
                record.p95TimeToPreviewMs()
        );
    }
}
