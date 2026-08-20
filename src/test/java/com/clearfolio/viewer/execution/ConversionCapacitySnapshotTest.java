package com.clearfolio.viewer.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ConversionCapacitySnapshotTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-11T05:00:00Z");

    @Test
    void reportsRemainingCapacityAcrossWorkersAndQueue() {
        ConversionCapacitySnapshot snapshot = new ConversionCapacitySnapshot(
                4,
                3,
                8,
                5,
                OBSERVED_AT
        );

        assertEquals(4, snapshot.workerCapacity());
        assertEquals(3, snapshot.activeWorkers());
        assertEquals(8, snapshot.queueCapacity());
        assertEquals(5, snapshot.queuedJobs());
        assertEquals(12L, snapshot.totalCapacity());
        assertEquals(4L, snapshot.remainingCapacity());
        assertFalse(snapshot.saturated());
        assertEquals(OBSERVED_AT, snapshot.observedAt());
    }

    @Test
    void treatsZeroQueueCapacityAsValidDirectHandoffCapacity() {
        ConversionCapacitySnapshot available = new ConversionCapacitySnapshot(
                2,
                1,
                0,
                0,
                OBSERVED_AT
        );
        ConversionCapacitySnapshot saturated = new ConversionCapacitySnapshot(
                2,
                2,
                0,
                0,
                OBSERVED_AT
        );

        assertEquals(1L, available.remainingCapacity());
        assertFalse(available.saturated());
        assertEquals(0L, saturated.remainingCapacity());
        assertTrue(saturated.saturated());
    }

    @Test
    void avoidsIntegerOverflowInAggregateCapacity() {
        ConversionCapacitySnapshot snapshot = new ConversionCapacitySnapshot(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                OBSERVED_AT
        );

        assertEquals(2L * Integer.MAX_VALUE, snapshot.totalCapacity());
        assertEquals(0L, snapshot.remainingCapacity());
        assertTrue(snapshot.saturated());
    }

    @Test
    void rejectsImpossibleCapacityState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(0, 0, 0, 0, OBSERVED_AT)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(2, -1, 0, 0, OBSERVED_AT)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(2, 3, 0, 0, OBSERVED_AT)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(2, 1, -1, 0, OBSERVED_AT)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(2, 1, 1, -1, OBSERVED_AT)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionCapacitySnapshot(2, 1, 1, 2, OBSERVED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new ConversionCapacitySnapshot(2, 1, 1, 0, null)
        );
    }
}
