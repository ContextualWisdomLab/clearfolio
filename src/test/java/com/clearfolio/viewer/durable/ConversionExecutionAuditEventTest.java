package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionExecutionAuditEventTest {

    private static final String TENANT_FINGERPRINT = "v1:" + "a".repeat(32);
    private static final String JOB_FINGERPRINT = "v1:" + "b".repeat(32);

    @Test
    void eventPreservesOnlyPseudonymousExecutionAuthority() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T02:30:00Z");

        ConversionExecutionAuditEvent event = ConversionExecutionAuditEvent.create(
                eventId,
                TENANT_FINGERPRINT,
                JOB_FINGERPRINT,
                7L,
                2,
                ConversionExecutionEventType.CLAIMED,
                "worker_claimed",
                occurredAt
        );

        assertEquals(eventId, event.eventId());
        assertEquals(TENANT_FINGERPRINT, event.tenantFingerprint());
        assertEquals(JOB_FINGERPRINT, event.jobFingerprint());
        assertEquals(7L, event.generation());
        assertEquals(2, event.attempt());
        assertEquals(ConversionExecutionEventType.CLAIMED, event.eventType());
        assertEquals("worker_claimed", event.reasonCode());
        assertEquals(occurredAt, event.occurredAt());
    }

    @Test
    void retryableFailureHasDistinctAuditVocabulary() {
        ConversionExecutionAuditEvent event = ConversionExecutionAuditEvent.create(
                UUID.randomUUID(),
                TENANT_FINGERPRINT,
                JOB_FINGERPRINT,
                2L,
                3,
                ConversionExecutionEventType.RETRYABLE_FAILED,
                "conversion_retryable_failed",
                Instant.parse("2026-08-11T02:30:00Z")
        );

        assertEquals(ConversionExecutionEventType.RETRYABLE_FAILED, event.eventType());
        assertEquals("conversion_retryable_failed", event.reasonCode());
    }

    @Test
    void reasonCodeMayBeAbsentWithoutInventingFailureDetail() {
        ConversionExecutionAuditEvent event = ConversionExecutionAuditEvent.create(
                UUID.randomUUID(),
                TENANT_FINGERPRINT,
                JOB_FINGERPRINT,
                1L,
                0,
                ConversionExecutionEventType.ACCEPTED,
                null,
                Instant.parse("2026-08-11T02:30:00Z")
        );

        assertNull(event.reasonCode());
    }

    @Test
    void exactExecutionMatchUsesPseudonymousJobAndGenerationOnly() {
        ConversionExecutionAuditEvent event = ConversionExecutionAuditEvent.create(
                UUID.randomUUID(),
                TENANT_FINGERPRINT,
                JOB_FINGERPRINT,
                4L,
                1,
                ConversionExecutionEventType.SUCCEEDED,
                "conversion_completed",
                Instant.parse("2026-08-11T02:30:00Z")
        );

        assertTrue(event.matchesExecution(JOB_FINGERPRINT, 4L));
        assertFalse(event.matchesExecution("v1:" + "c".repeat(32), 4L));
        assertFalse(event.matchesExecution(JOB_FINGERPRINT, 5L));
        assertFalse(event.matchesExecution(null, 4L));
    }

    @Test
    void fingerprintsMustBeVersionedKeyedAuditPseudonyms() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T02:30:00Z");

        assertThrows(NullPointerException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, null, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(NullPointerException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, null, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, "a".repeat(64), JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, "a".repeat(32), JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, "v1:" + "B".repeat(32), 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, "v1:" + "g".repeat(32), 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, "unsafe version:" + "a".repeat(32), JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, "v".repeat(33) + ":" + "a".repeat(32), JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, "unavailable:v1", JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
    }

    @Test
    void authorityAndTimestampFailClosed() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T02:30:00Z");

        assertThrows(NullPointerException.class, () -> ConversionExecutionAuditEvent.create(
                null, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(NullPointerException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                null, null, occurredAt));
        assertThrows(NullPointerException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 0L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, -1L, 0,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, -1,
                ConversionExecutionEventType.ACCEPTED, null, occurredAt));
    }

    @Test
    void reasonCodeIsBoundedLowCardinalityVocabulary() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T02:30:00Z");

        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.TERMINAL_FAILED, "", occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.TERMINAL_FAILED, "contains document.docx", occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.TERMINAL_FAILED, "x".repeat(65), occurredAt));
        assertThrows(IllegalArgumentException.class, () -> ConversionExecutionAuditEvent.create(
                eventId, TENANT_FINGERPRINT, JOB_FINGERPRINT, 1L, 0,
                ConversionExecutionEventType.TERMINAL_FAILED, "Worker_Failed", occurredAt));
    }
}
