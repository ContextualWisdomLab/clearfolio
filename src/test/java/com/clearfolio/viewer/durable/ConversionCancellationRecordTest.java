package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionCancellationRecordTest {

    @Test
    void requestBindsTenantJobGenerationAndRequestIdentity() {
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");

        ConversionCancellationRecord record = ConversionCancellationRecord.requested(
                requestId,
                " tenant-a ",
                jobId,
                4L,
                requestedAt
        );

        assertEquals(requestId, record.requestId());
        assertEquals("tenant-a", record.tenantId());
        assertEquals(jobId, record.jobId());
        assertEquals(4L, record.generation());
        assertEquals(requestedAt, record.requestedAt());
        assertEquals(ConversionCancellationState.REQUESTED, record.state());
        assertTrue(record.terminalAt().isEmpty());
    }

    @Test
    void cancellationWinsRaceMonotonicallyAndOnlyExactReplayIsIdempotent() {
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");
        Instant cancelledAt = requestedAt.plusSeconds(1);
        ConversionCancellationRecord requested = request(requestedAt);

        ConversionCancellationRecord cancelled = requested.markCancelled(cancelledAt);

        assertEquals(ConversionCancellationState.CANCELLED, cancelled.state());
        assertEquals(cancelledAt, cancelled.terminalAt().orElseThrow());
        assertSame(cancelled, cancelled.markCancelled(cancelledAt));
        assertThrows(IllegalStateException.class,
                () -> cancelled.markCancelled(cancelledAt.plusNanos(1)));
        assertThrows(IllegalStateException.class,
                () -> cancelled.markCompleted(cancelledAt.plusSeconds(1)));
    }

    @Test
    void completionWinsRaceMonotonicallyAndOnlyExactReplayIsIdempotent() {
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");
        Instant completedAt = requestedAt.plusSeconds(1);
        ConversionCancellationRecord requested = request(requestedAt);

        ConversionCancellationRecord completed = requested.markCompleted(completedAt);

        assertEquals(ConversionCancellationState.COMPLETED_BEFORE_CANCELLATION, completed.state());
        assertEquals(completedAt, completed.terminalAt().orElseThrow());
        assertSame(completed, completed.markCompleted(completedAt));
        assertThrows(IllegalStateException.class,
                () -> completed.markCompleted(completedAt.plusNanos(1)));
        assertThrows(IllegalStateException.class,
                () -> completed.markCancelled(completedAt.plusSeconds(1)));
    }

    @Test
    void terminalTransitionCannotPredateRequest() {
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");
        ConversionCancellationRecord requested = request(requestedAt);

        assertThrows(IllegalArgumentException.class,
                () -> requested.markCancelled(requestedAt.minusNanos(1)));
        assertThrows(IllegalArgumentException.class,
                () -> requested.markCompleted(requestedAt.minusNanos(1)));
        assertThrows(NullPointerException.class, () -> requested.markCancelled(null));
        assertThrows(NullPointerException.class, () -> requested.markCompleted(null));
    }

    @Test
    void requestConstructionFailsClosedForInvalidAuthority() {
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");
        String oversizedTenant = "x".repeat(257);

        assertThrows(NullPointerException.class,
                () -> ConversionCancellationRecord.requested(null, "tenant", jobId, 1L, requestedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionCancellationRecord.requested(requestId, null, jobId, 1L, requestedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionCancellationRecord.requested(requestId, "tenant", null, 1L, requestedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionCancellationRecord.requested(requestId, "tenant", jobId, 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCancellationRecord.requested(requestId, "   ", jobId, 1L, requestedAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCancellationRecord.requested(requestId, oversizedTenant, jobId, 1L, requestedAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCancellationRecord.requested(requestId, "tenant", jobId, 0L, requestedAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCancellationRecord.requested(requestId, "tenant", jobId, -1L, requestedAt));
    }

    @Test
    void requestRejectsControlCorruptedTenantAuthority() {
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-08-11T01:00:00Z");

        for (String tenantId : List.of(
                "tenant\u0000-a",
                "tenant\n-a",
                "tenant\u001B-a",
                "tenant\u2028-a",
                "tenant\u2029-a"
        )) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ConversionCancellationRecord.requested(
                            requestId,
                            tenantId,
                            jobId,
                            1L,
                            requestedAt
                    )
            );
            assertEquals("tenantId must not contain control characters", exception.getMessage());
        }
    }

    @Test
    void generationFenceRejectsAnotherTenantJobOrGeneration() {
        UUID jobId = UUID.randomUUID();
        ConversionCancellationRecord record = ConversionCancellationRecord.requested(
                UUID.randomUUID(),
                "tenant-a",
                jobId,
                8L,
                Instant.parse("2026-08-11T01:00:00Z")
        );

        assertTrue(record.authorizes("tenant-a", jobId, 8L));
        assertFalse(record.authorizes(" tenant-a ", jobId, 8L));
        assertFalse(record.authorizes("tenant\u0000-a", jobId, 8L));
        assertFalse(record.authorizes("tenant-b", jobId, 8L));
        assertFalse(record.authorizes("tenant-a", UUID.randomUUID(), 8L));
        assertFalse(record.authorizes("tenant-a", jobId, 9L));
        assertFalse(record.authorizes(null, jobId, 8L));
        assertFalse(record.authorizes("tenant-a", null, 8L));
    }

    private static ConversionCancellationRecord request(Instant requestedAt) {
        return ConversionCancellationRecord.requested(
                UUID.randomUUID(),
                "tenant-a",
                UUID.randomUUID(),
                1L,
                requestedAt
        );
    }
}
