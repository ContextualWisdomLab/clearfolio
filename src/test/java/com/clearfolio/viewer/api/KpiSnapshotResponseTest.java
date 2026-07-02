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

    private ConversionJob succeededJob(Instant startedAt, Instant completedAt) {
        ConversionJob job = mock(ConversionJob.class);
        when(job.getStatus()).thenReturn(ConversionJobStatus.SUCCEEDED);
        when(job.getStartedAt()).thenReturn(startedAt);
        when(job.getCompletedAt()).thenReturn(completedAt);
        when(job.isDeadLettered()).thenReturn(false);
        return job;
    }
}
