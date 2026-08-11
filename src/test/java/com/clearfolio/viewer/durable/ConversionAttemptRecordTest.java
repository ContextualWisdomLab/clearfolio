package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionAttemptRecordTest {

    @Test
    void claimBindsExactAttemptAndWorkerAuthority() {
        UUID attemptId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");

        ConversionAttemptRecord record = ConversionAttemptRecord.claim(
                attemptId,
                jobId,
                4L,
                2,
                leaseId,
                claimedAt
        );

        assertEquals(attemptId, record.attemptId());
        assertEquals(jobId, record.jobId());
        assertEquals(4L, record.generation());
        assertEquals(2, record.attempt());
        assertEquals(leaseId, record.leaseId());
        assertEquals(claimedAt, record.claimedAt());
        assertEquals(ConversionAttemptState.CLAIMED, record.state());
        assertNull(record.finishedAt());
    }

    @Test
    void publicationAuthorityRequiresExactJobGenerationAndLease() {
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        ConversionAttemptRecord record = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                jobId,
                3L,
                1,
                leaseId,
                Instant.parse("2026-08-11T02:00:00Z")
        );

        assertTrue(record.authorizes(jobId, 3L, leaseId));
        assertFalse(record.authorizes(UUID.randomUUID(), 3L, leaseId));
        assertFalse(record.authorizes(jobId, 4L, leaseId));
        assertFalse(record.authorizes(jobId, 3L, UUID.randomUUID()));
        assertFalse(record.authorizes(null, 3L, leaseId));
        assertFalse(record.authorizes(jobId, 3L, null));
    }

    @Test
    void failedTerminalAttemptCannotAuthorizeArtifactPublication() {
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");
        ConversionAttemptRecord claimed = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                jobId,
                3L,
                1,
                leaseId,
                claimedAt
        );

        assertTrue(claimed.authorizes(jobId, 3L, leaseId));
        assertTrue(claimed.finish(ConversionAttemptState.SUCCEEDED, claimedAt.plusSeconds(1L))
                .authorizes(jobId, 3L, leaseId));
        assertFalse(claimed.finish(ConversionAttemptState.RETRYABLE_FAILED, claimedAt.plusSeconds(1L))
                .authorizes(jobId, 3L, leaseId));
        assertFalse(claimed.finish(ConversionAttemptState.TERMINAL_FAILED, claimedAt.plusSeconds(1L))
                .authorizes(jobId, 3L, leaseId));
    }

    @Test
    void terminalOutcomeIsMonotonicAndExactReplayIsIdempotent() {
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");
        Instant finishedAt = claimedAt.plusSeconds(7L);
        ConversionAttemptRecord claimed = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2L,
                1,
                UUID.randomUUID(),
                claimedAt
        );

        ConversionAttemptRecord succeeded = claimed.finish(
                ConversionAttemptState.SUCCEEDED,
                finishedAt
        );

        assertEquals(ConversionAttemptState.SUCCEEDED, succeeded.state());
        assertEquals(finishedAt, succeeded.finishedAt());
        assertSame(succeeded, succeeded.finish(ConversionAttemptState.SUCCEEDED, finishedAt));
        assertThrows(IllegalStateException.class,
                () -> succeeded.finish(ConversionAttemptState.TERMINAL_FAILED, finishedAt));
        assertThrows(IllegalStateException.class,
                () -> succeeded.finish(ConversionAttemptState.SUCCEEDED, finishedAt.plusSeconds(1L)));
    }

    @Test
    void retryableAndTerminalFailureAreRepresentableTerminalOutcomes() {
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");
        ConversionAttemptRecord first = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                9L,
                3,
                UUID.randomUUID(),
                claimedAt
        );
        ConversionAttemptRecord second = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                9L,
                4,
                UUID.randomUUID(),
                claimedAt
        );

        assertEquals(
                ConversionAttemptState.RETRYABLE_FAILED,
                first.finish(ConversionAttemptState.RETRYABLE_FAILED, claimedAt).state()
        );
        assertEquals(
                ConversionAttemptState.TERMINAL_FAILED,
                second.finish(ConversionAttemptState.TERMINAL_FAILED, claimedAt.plusNanos(1L)).state()
        );
    }

    @Test
    void finishRejectsNonTerminalOrInvalidTimeAuthority() {
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");
        ConversionAttemptRecord record = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                1,
                UUID.randomUUID(),
                claimedAt
        );

        assertThrows(NullPointerException.class, () -> record.finish(null, claimedAt));
        assertThrows(NullPointerException.class,
                () -> record.finish(ConversionAttemptState.SUCCEEDED, null));
        assertEquals(
                "terminalState must be terminal",
                assertThrows(IllegalArgumentException.class,
                        () -> record.finish(ConversionAttemptState.CLAIMED, claimedAt)).getMessage()
        );
        assertEquals(
                "completionTime must not precede claimedAt",
                assertThrows(IllegalArgumentException.class,
                        () -> record.finish(ConversionAttemptState.SUCCEEDED, claimedAt.minusNanos(1L))).getMessage()
        );
    }

    @Test
    void claimFailsClosedForMissingOrNonPositiveAuthority() {
        UUID attemptId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Instant claimedAt = Instant.parse("2026-08-11T02:00:00Z");

        assertThrows(NullPointerException.class,
                () -> ConversionAttemptRecord.claim(null, jobId, 1L, 1, leaseId, claimedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionAttemptRecord.claim(attemptId, null, 1L, 1, leaseId, claimedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionAttemptRecord.claim(attemptId, jobId, 1L, 1, null, claimedAt));
        assertThrows(NullPointerException.class,
                () -> ConversionAttemptRecord.claim(attemptId, jobId, 1L, 1, leaseId, null));
        assertEquals(
                "generation must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> ConversionAttemptRecord.claim(attemptId, jobId, 0L, 1, leaseId, claimedAt)).getMessage()
        );
        assertEquals(
                "generation must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> ConversionAttemptRecord.claim(attemptId, jobId, -1L, 1, leaseId, claimedAt)).getMessage()
        );
        assertEquals(
                "attempt must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> ConversionAttemptRecord.claim(attemptId, jobId, 1L, 0, leaseId, claimedAt)).getMessage()
        );
        assertEquals(
                "attempt must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> ConversionAttemptRecord.claim(attemptId, jobId, 1L, -1, leaseId, claimedAt)).getMessage()
        );
    }
}
