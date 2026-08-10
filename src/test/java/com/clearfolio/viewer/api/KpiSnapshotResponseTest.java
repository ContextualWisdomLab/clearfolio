package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

class KpiSnapshotResponseTest {

    @Test
    void fromSkipsPreviewTimingWhenSucceededJobsDoNotHaveCompleteTimestamps() {
        Instant now = Instant.now();
        ConversionJob missingStartedAt = succeededJob(null, now);
        ConversionJob missingCompletedAt = succeededJob(now, null);

        KpiSnapshotResponse response = KpiSnapshotResponse.from(List.of(missingStartedAt, missingCompletedAt));

        assertEquals(2, response.totalJobs());
        assertEquals(2, response.succeededJobs());
        assertEquals(1.0, response.conversionSuccessRate());
        assertNull(response.p95TimeToPreviewMs());
    }

    @Test
    void conversionSuccessRateUsesOnlyTerminalOutcomes() {
        KpiSnapshotResponse response = KpiSnapshotResponse.from(List.of(
                job(ConversionJobStatus.SUCCEEDED),
                job(ConversionJobStatus.SUCCEEDED),
                job(ConversionJobStatus.FAILED),
                job(ConversionJobStatus.SUBMITTED),
                job(ConversionJobStatus.PROCESSING)
        ));

        assertEquals(5, response.totalJobs());
        assertEquals(1, response.submittedJobs());
        assertEquals(1, response.processingJobs());
        assertEquals(2, response.succeededJobs());
        assertEquals(1, response.failedJobs());
        assertEquals(2.0 / 3.0, response.conversionSuccessRate(), 1.0e-12);
    }

    @Test
    void conversionSuccessRateIsZeroUntilOneJobReachesATerminalOutcome() {
        KpiSnapshotResponse response = KpiSnapshotResponse.from(List.of(
                job(ConversionJobStatus.SUBMITTED),
                job(ConversionJobStatus.PROCESSING)
        ));

        assertEquals(0.0, response.conversionSuccessRate());
    }

    private ConversionJob succeededJob(Instant startedAt, Instant completedAt) {
        ConversionJob job = job(ConversionJobStatus.SUCCEEDED);
        when(job.getStartedAt()).thenReturn(startedAt);
        when(job.getCompletedAt()).thenReturn(completedAt);
        return job;
    }

    private ConversionJob job(ConversionJobStatus status) {
        ConversionJob job = mock(ConversionJob.class);
        when(job.getStatus()).thenReturn(status);
        when(job.isDeadLettered()).thenReturn(false);
        return job;
    }
}
