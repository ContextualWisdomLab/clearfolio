package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJobStatus;

class ConversionJobLifecycleEventTest {

    @Test
    void createsVersionedLifecycleEvent() {
        UUID eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-02T00:00:00Z");

        ConversionJobLifecycleEvent event = new ConversionJobLifecycleEvent(
                eventId,
                jobId,
                "tenant-a",
                "conversion.job.submitted",
                ConversionJobLifecycleEvent.CURRENT_VERSION,
                occurredAt,
                null,
                ConversionJobStatus.SUBMITTED,
                0,
                null
        );

        assertEquals(eventId, event.eventId());
        assertEquals(jobId, event.jobId());
        assertEquals("tenant-a", event.tenantId());
        assertEquals(1, event.eventVersion());
        assertEquals(occurredAt, event.occurredAt());
    }

    @Test
    void rejectsBlankTenantId() {
        assertThrows(IllegalArgumentException.class, () -> event(" ", "conversion.job.submitted", 1, 0));
    }

    @Test
    void rejectsBlankEventType() {
        assertThrows(IllegalArgumentException.class, () -> event("tenant-a", " ", 1, 0));
    }

    @Test
    void rejectsNonPositiveEventVersion() {
        assertThrows(IllegalArgumentException.class, () -> event("tenant-a", "conversion.job.submitted", 0, 0));
    }

    @Test
    void rejectsNegativeAttemptCount() {
        assertThrows(IllegalArgumentException.class, () -> event("tenant-a", "conversion.job.submitted", 1, -1));
    }

    private ConversionJobLifecycleEvent event(
            String tenantId,
            String eventType,
            int eventVersion,
            int attemptCount
    ) {
        return new ConversionJobLifecycleEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                eventType,
                eventVersion,
                Instant.now(),
                null,
                ConversionJobStatus.SUBMITTED,
                attemptCount,
                null
        );
    }
}
