package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionOutboxRecordTest {

    @Test
    void pendingRecordBindsExactAcceptedJobGeneration() {
        UUID outboxId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-08-11T00:00:00Z");

        ConversionOutboxRecord record = ConversionOutboxRecord.pending(
                outboxId,
                jobId,
                4L,
                acceptedAt
        );

        assertEquals(outboxId, record.outboxId());
        assertEquals(jobId, record.jobId());
        assertEquals(4L, record.generation());
        assertEquals(acceptedAt, record.acceptedAt());
        assertTrue(record.isPending());
        assertFalse(record.dispatchedAt().isPresent());
    }

    @Test
    void dispatchCreatesTerminalReceiptWithoutChangingAcceptanceIdentity() {
        UUID outboxId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-08-11T00:00:00Z");
        Instant dispatchedAt = acceptedAt.plusSeconds(2);
        ConversionOutboxRecord pending = ConversionOutboxRecord.pending(
                outboxId,
                jobId,
                9L,
                acceptedAt
        );

        ConversionOutboxRecord dispatched = pending.markDispatched(dispatchedAt);

        assertEquals(outboxId, dispatched.outboxId());
        assertEquals(jobId, dispatched.jobId());
        assertEquals(9L, dispatched.generation());
        assertEquals(acceptedAt, dispatched.acceptedAt());
        assertFalse(dispatched.isPending());
        assertEquals(dispatchedAt, dispatched.dispatchedAt().orElseThrow());
    }

    @Test
    void duplicateDispatchAcknowledgementIsIdempotent() {
        Instant acceptedAt = Instant.parse("2026-08-11T00:00:00Z");
        ConversionOutboxRecord dispatched = ConversionOutboxRecord.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                acceptedAt
        ).markDispatched(acceptedAt.plusSeconds(1));

        assertSame(dispatched, dispatched.markDispatched(acceptedAt.plusSeconds(2)));
    }

    @Test
    void dispatchCannotPredateDurableAcceptance() {
        Instant acceptedAt = Instant.parse("2026-08-11T00:00:00Z");
        ConversionOutboxRecord pending = ConversionOutboxRecord.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                acceptedAt
        );

        assertThrows(IllegalArgumentException.class,
                () -> pending.markDispatched(acceptedAt.minusNanos(1)));
        assertThrows(NullPointerException.class, () -> pending.markDispatched(null));
    }

    @Test
    void constructionFailsClosedForMissingOrInvalidAuthority() {
        UUID outboxId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-08-11T00:00:00Z");

        assertThrows(NullPointerException.class,
                () -> ConversionOutboxRecord.pending(null, jobId, 1L, acceptedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionOutboxRecord.pending(outboxId, null, 1L, acceptedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionOutboxRecord.pending(outboxId, jobId, 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionOutboxRecord.pending(outboxId, jobId, 0L, acceptedAt));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionOutboxRecord.pending(outboxId, jobId, -1L, acceptedAt));
    }
}
