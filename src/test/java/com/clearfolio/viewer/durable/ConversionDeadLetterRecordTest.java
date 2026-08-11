package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionDeadLetterRecordTest {

    @Test
    void deadLetterRecordPreservesGenerationAttemptAndControlledReason() {
        UUID deadLetterId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant failedAt = Instant.parse("2026-08-12T00:00:00Z");

        ConversionDeadLetterRecord record = ConversionDeadLetterRecord.record(
                deadLetterId,
                jobId,
                7L,
                3,
                failedAt,
                "CONVERTER_TIMEOUT"
        );

        assertEquals(deadLetterId, record.deadLetterId());
        assertEquals(jobId, record.jobId());
        assertEquals(7L, record.generation());
        assertEquals(3, record.attempt());
        assertEquals(failedAt, record.failedAt());
        assertEquals("CONVERTER_TIMEOUT", record.reasonCode());
    }

    @Test
    void deadLetterAuthorityRequiresExactJobGenerationAndAttempt() {
        UUID jobId = UUID.randomUUID();
        ConversionDeadLetterRecord record = ConversionDeadLetterRecord.record(
                UUID.randomUUID(),
                jobId,
                2L,
                1,
                Instant.parse("2026-08-12T00:00:00Z"),
                "RETRY_EXHAUSTED"
        );

        assertTrue(record.authorizes(jobId, 2L, 1));
        assertFalse(record.authorizes(UUID.randomUUID(), 2L, 1));
        assertFalse(record.authorizes(jobId, 3L, 1));
        assertFalse(record.authorizes(jobId, 2L, 2));
        assertFalse(record.authorizes(null, 2L, 1));
    }

    @Test
    void reasonCodeRejectsHighCardinalityOrAmbiguousFailureText() {
        UUID deadLetterId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant failedAt = Instant.parse("2026-08-12T00:00:00Z");

        assertThrows(NullPointerException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, null));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, ""));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, "converter timed out"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, "CONVERTER-TIMEOUT"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, "A".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, "_RETRY"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, failedAt, "TENANT_12345"));
    }

    @Test
    void constructionFailsClosedForMissingOrImpossibleFailureAuthority() {
        UUID deadLetterId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant failedAt = Instant.parse("2026-08-12T00:00:00Z");

        assertThrows(NullPointerException.class, () -> ConversionDeadLetterRecord.record(
                null, jobId, 1L, 1, failedAt, "RETRY_EXHAUSTED"));
        assertThrows(NullPointerException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, null, 1L, 1, failedAt, "RETRY_EXHAUSTED"));
        assertThrows(NullPointerException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 1, null, "RETRY_EXHAUSTED"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 0L, 1, failedAt, "RETRY_EXHAUSTED"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, -1L, 1, failedAt, "RETRY_EXHAUSTED"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, 0, failedAt, "RETRY_EXHAUSTED"));
        assertThrows(IllegalArgumentException.class, () -> ConversionDeadLetterRecord.record(
                deadLetterId, jobId, 1L, -1, failedAt, "RETRY_EXHAUSTED"));
    }
}
