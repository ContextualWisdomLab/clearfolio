package com.clearfolio.viewer.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionAdmissionResultTest {

    @Test
    void acceptedCarriesOnlyRecoverableJobIdentity() {
        UUID jobId = UUID.randomUUID();

        ConversionAdmissionResult result = ConversionAdmissionResult.accepted(jobId);

        assertEquals(ConversionAdmissionDecision.ACCEPTED, result.decision());
        assertEquals(jobId, result.jobId().orElseThrow());
        assertTrue(result.retryAfter().isEmpty());
    }

    @Test
    void idempotentReplayCarriesCanonicalJobIdentity() {
        UUID jobId = UUID.randomUUID();

        ConversionAdmissionResult result = ConversionAdmissionResult.idempotentReplay(jobId);

        assertEquals(ConversionAdmissionDecision.IDEMPOTENT_REPLAY, result.decision());
        assertEquals(jobId, result.jobId().orElseThrow());
        assertTrue(result.retryAfter().isEmpty());
    }

    @Test
    void capacityRejectionCarriesRetryGuidanceWithoutJobIdentity() {
        Duration retryAfter = Duration.ofSeconds(3);

        ConversionAdmissionResult result = ConversionAdmissionResult.capacityRejected(retryAfter);

        assertEquals(ConversionAdmissionDecision.CAPACITY_REJECTED, result.decision());
        assertTrue(result.jobId().isEmpty());
        assertEquals(retryAfter, result.retryAfter().orElseThrow());
    }

    @Test
    void acceptedAndReplayRejectMissingJobIdentity() {
        assertThrows(NullPointerException.class, () -> ConversionAdmissionResult.accepted(null));
        assertThrows(NullPointerException.class, () -> ConversionAdmissionResult.idempotentReplay(null));
    }

    @Test
    void capacityRejectionRequiresPositiveRetryGuidance() {
        assertThrows(NullPointerException.class, () -> ConversionAdmissionResult.capacityRejected(null));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionAdmissionResult.capacityRejected(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionAdmissionResult.capacityRejected(Duration.ofMillis(-1)));
    }

    @Test
    void factoriesDoNotExposeContradictoryAuthority() {
        ConversionAdmissionResult accepted = ConversionAdmissionResult.accepted(UUID.randomUUID());
        ConversionAdmissionResult replay = ConversionAdmissionResult.idempotentReplay(UUID.randomUUID());
        ConversionAdmissionResult rejected = ConversionAdmissionResult.capacityRejected(Duration.ofSeconds(1));

        assertFalse(accepted.retryAfter().isPresent());
        assertFalse(replay.retryAfter().isPresent());
        assertFalse(rejected.jobId().isPresent());
    }
}
