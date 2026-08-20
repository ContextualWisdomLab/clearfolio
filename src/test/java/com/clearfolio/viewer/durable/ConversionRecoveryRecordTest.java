package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionRecoveryRecordTest {

    @Test
    void recoveryRecordBindsExactStaleLeaseGenerationAndAttempt() {
        UUID recoveryId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID staleLeaseId = UUID.randomUUID();
        Instant recoveredAt = Instant.parse("2026-08-12T00:00:00Z");
        Instant resumeNotBefore = recoveredAt.plusSeconds(30);

        ConversionRecoveryRecord record = ConversionRecoveryRecord.recover(
                recoveryId,
                jobId,
                4L,
                2,
                staleLeaseId,
                recoveredAt,
                resumeNotBefore
        );

        assertEquals(recoveryId, record.recoveryId());
        assertEquals(jobId, record.jobId());
        assertEquals(4L, record.generation());
        assertEquals(2, record.attempt());
        assertEquals(staleLeaseId, record.staleLeaseId());
        assertEquals(recoveredAt, record.recoveredAt());
        assertEquals(resumeNotBefore, record.resumeNotBefore());
    }

    @Test
    void recoveryAuthorityRequiresExactJobGenerationAttemptAndStaleLease() {
        UUID jobId = UUID.randomUUID();
        UUID staleLeaseId = UUID.randomUUID();
        ConversionRecoveryRecord record = ConversionRecoveryRecord.recover(
                UUID.randomUUID(),
                jobId,
                3L,
                1,
                staleLeaseId,
                Instant.parse("2026-08-12T00:00:00Z"),
                Instant.parse("2026-08-12T00:00:01Z")
        );

        assertTrue(record.authorizes(jobId, 3L, 1, staleLeaseId));
        assertFalse(record.authorizes(UUID.randomUUID(), 3L, 1, staleLeaseId));
        assertFalse(record.authorizes(jobId, 4L, 1, staleLeaseId));
        assertFalse(record.authorizes(jobId, 3L, 2, staleLeaseId));
        assertFalse(record.authorizes(jobId, 3L, 1, UUID.randomUUID()));
        assertFalse(record.authorizes(null, 3L, 1, staleLeaseId));
        assertFalse(record.authorizes(jobId, 3L, 1, null));
    }

    @Test
    void resumeEligibilityUsesPersistedBoundaryInclusively() {
        Instant recoveredAt = Instant.parse("2026-08-12T00:00:00Z");
        Instant resumeNotBefore = recoveredAt.plusSeconds(30);
        ConversionRecoveryRecord record = ConversionRecoveryRecord.recover(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                0,
                UUID.randomUUID(),
                recoveredAt,
                resumeNotBefore
        );

        assertFalse(record.isEligibleAt(resumeNotBefore.minusNanos(1)));
        assertTrue(record.isEligibleAt(resumeNotBefore));
        assertTrue(record.isEligibleAt(resumeNotBefore.plusNanos(1)));
        assertThrows(NullPointerException.class, () -> record.isEligibleAt(null));
    }

    @Test
    void constructionFailsClosedForMissingOrImpossibleRecoveryAuthority() {
        UUID recoveryId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID staleLeaseId = UUID.randomUUID();
        Instant recoveredAt = Instant.parse("2026-08-12T00:00:00Z");
        Instant resumeNotBefore = recoveredAt.plusSeconds(1);

        assertThrows(NullPointerException.class, () -> ConversionRecoveryRecord.recover(
                null, jobId, 1L, 0, staleLeaseId, recoveredAt, resumeNotBefore));
        assertThrows(NullPointerException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, null, 1L, 0, staleLeaseId, recoveredAt, resumeNotBefore));
        assertThrows(NullPointerException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 1L, 0, null, recoveredAt, resumeNotBefore));
        assertThrows(NullPointerException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 1L, 0, staleLeaseId, null, resumeNotBefore));
        assertThrows(NullPointerException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 1L, 0, staleLeaseId, recoveredAt, null));
        assertThrows(IllegalArgumentException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 0L, 0, staleLeaseId, recoveredAt, resumeNotBefore));
        assertThrows(IllegalArgumentException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, -1L, 0, staleLeaseId, recoveredAt, resumeNotBefore));
        assertThrows(IllegalArgumentException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 1L, -1, staleLeaseId, recoveredAt, resumeNotBefore));
        assertThrows(IllegalArgumentException.class, () -> ConversionRecoveryRecord.recover(
                recoveryId, jobId, 1L, 0, staleLeaseId, recoveredAt, recoveredAt.minusNanos(1)));
    }
}
