package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Verifies low-cardinality execution-duration evidence for cleanup recovery.
 */
class ArtifactDeletionMetricsDurationTest {

    @Test
    void recoveryDurationsExposeRunCountTotalAndMaximumWithoutDimensions() {
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(new ArtifactDeletionLedger());

        metrics.recordRecoveryBatchDuration(Duration.ofMillis(7));
        metrics.recordRecoveryBatchDuration(Duration.ofMillis(11));
        metrics.recordRecoveryBatchDuration(Duration.ZERO);

        assertEquals(3, metrics.recoveryBatchRuns());
        assertEquals(Duration.ofMillis(18), metrics.recoveryBatchTotalDuration());
        assertEquals(Duration.ofMillis(11), metrics.recoveryBatchMaximumDuration());
    }

    @Test
    void negativeAndMissingDurationsFailFastWithoutChangingEvidence() {
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(new ArtifactDeletionLedger());

        assertThrows(NullPointerException.class, () -> metrics.recordRecoveryBatchDuration(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.recordRecoveryBatchDuration(Duration.ofNanos(-1))
        );

        assertEquals(0, metrics.recoveryBatchRuns());
        assertEquals(Duration.ZERO, metrics.recoveryBatchTotalDuration());
        assertEquals(Duration.ZERO, metrics.recoveryBatchMaximumDuration());
    }
}
