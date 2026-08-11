package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionRetryRecordTest {

    @Test
    void retryRecordBindsExactJobGenerationAttemptAndDueTime() {
        UUID retryId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant dueAt = Instant.parse("2026-08-11T01:00:00Z");

        ConversionRetryRecord record = ConversionRetryRecord.schedule(
                retryId,
                jobId,
                5L,
                2,
                dueAt
        );

        assertEquals(retryId, record.retryId());
        assertEquals(jobId, record.jobId());
        assertEquals(5L, record.generation());
        assertEquals(2, record.attempt());
        assertEquals(dueAt, record.dueAt());
    }

    @Test
    void dueEvaluationUsesPersistedDueTimeInclusively() {
        Instant dueAt = Instant.parse("2026-08-11T01:00:00Z");
        ConversionRetryRecord record = ConversionRetryRecord.schedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                1,
                dueAt
        );

        assertFalse(record.isDue(dueAt.minusNanos(1)));
        assertTrue(record.isDue(dueAt));
        assertTrue(record.isDue(dueAt.plusNanos(1)));
        assertThrows(NullPointerException.class, () -> record.isDue(null));
    }

    @Test
    void generationFenceRejectsAnotherJobOrGeneration() {
        UUID jobId = UUID.randomUUID();
        ConversionRetryRecord record = ConversionRetryRecord.schedule(
                UUID.randomUUID(),
                jobId,
                3L,
                1,
                Instant.parse("2026-08-11T01:00:00Z")
        );

        assertTrue(record.authorizes(jobId, 3L));
        assertFalse(record.authorizes(UUID.randomUUID(), 3L));
        assertFalse(record.authorizes(jobId, 4L));
        assertFalse(record.authorizes(null, 3L));
    }

    @Test
    void constructionFailsClosedForMissingOrNonPositiveAuthority() {
        UUID retryId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant dueAt = Instant.parse("2026-08-11T01:00:00Z");

        assertThrows(NullPointerException.class,
                () -> ConversionRetryRecord.schedule(null, jobId, 1L, 1, dueAt));
        assertThrows(NullPointerException.class,
                () -> ConversionRetryRecord.schedule(retryId, null, 1L, 1, dueAt));
        assertThrows(NullPointerException.class,
                () -> ConversionRetryRecord.schedule(retryId, jobId, 1L, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionRetryRecord.schedule(retryId, jobId, 0L, 1, dueAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionRetryRecord.schedule(retryId, jobId, -1L, 1, dueAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionRetryRecord.schedule(retryId, jobId, 1L, 0, dueAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionRetryRecord.schedule(retryId, jobId, 1L, -1, dueAt));
    }
}
