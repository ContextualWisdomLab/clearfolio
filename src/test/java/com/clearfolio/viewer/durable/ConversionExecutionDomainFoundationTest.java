package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.execution.ConversionAdmissionDecision;
import com.clearfolio.viewer.execution.ConversionAdmissionResult;
import com.clearfolio.viewer.execution.ConversionCapacitySnapshot;
import com.clearfolio.viewer.execution.ConversionWorkerLease;
import com.clearfolio.viewer.observability.ConversionTelemetryEvent;

class ConversionExecutionDomainFoundationTest {

    private static final String SOURCE_DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void acceptedDeliveryPreservesOneGenerationFencedAuthorityChain() {
        UUID jobId = UUID.randomUUID();
        long generation = 4L;
        Instant acceptedAt = Instant.parse("2026-08-20T13:00:00Z");
        UUID leaseId = UUID.randomUUID();

        ConversionIdempotencyIdentity idempotencyIdentity =
                new ConversionIdempotencyIdentity(
                        "tenant-a",
                        SOURCE_DIGEST,
                        "office-policy-v1",
                        "pdf-output-v1"
                );
        ConversionAdmissionResult admission = ConversionAdmissionResult.accepted(jobId);
        ConversionOutboxRecord outbox = ConversionOutboxRecord.pending(
                UUID.randomUUID(),
                jobId,
                generation,
                acceptedAt
        ).markDispatched(acceptedAt.plusMillis(1));
        UUID messageId = UUID.randomUUID();
        ConversionDeliveryReceipt delivery = new ConversionDeliveryReceipt(
                messageId,
                jobId,
                generation,
                acceptedAt.plusMillis(2)
        );
        ConversionWorkerLease lease = ConversionWorkerLease.issue(jobId, generation, leaseId);
        ConversionAttemptRecord attempt = ConversionAttemptRecord.claim(
                UUID.randomUUID(),
                jobId,
                generation,
                1,
                leaseId,
                acceptedAt.plusMillis(3)
        ).finish(ConversionAttemptState.SUCCEEDED, acceptedAt.plusMillis(4));
        ConversionTelemetryEvent telemetry = new ConversionTelemetryEvent(
                ConversionTelemetryEvent.EventType.EXECUTION,
                ConversionTelemetryEvent.Outcome.SUCCEEDED,
                acceptedAt.plusMillis(4)
        );
        ConversionExecutionAuditEvent audit = ConversionExecutionAuditEvent.create(
                UUID.randomUUID(),
                "v1:" + "a".repeat(32),
                "v1:" + "b".repeat(32),
                generation,
                1,
                ConversionExecutionEventType.SUCCEEDED,
                "conversion_completed",
                acceptedAt.plusMillis(4)
        );

        assertEquals(64, idempotencyIdentity.canonicalKey().length());
        assertEquals(ConversionAdmissionDecision.ACCEPTED, admission.decision());
        assertEquals(jobId, admission.jobId().orElseThrow());
        assertFalse(outbox.isPending());
        assertTrue(delivery.authorizes(messageId, jobId, generation));
        assertTrue(lease.authorizes(jobId, generation, leaseId));
        assertTrue(attempt.authorizes(jobId, generation, 1, leaseId));
        assertEquals("succeeded", telemetry.attributes().get("clearfolio.conversion.outcome"));
        assertTrue(audit.matchesExecution("v1:" + "b".repeat(32), generation));
    }

    @Test
    void saturationProducesRejectionWithoutInventingJobAuthority() {
        Instant observedAt = Instant.parse("2026-08-20T13:10:00Z");
        ConversionCapacitySnapshot capacity = new ConversionCapacitySnapshot(
                2,
                2,
                3,
                3,
                observedAt
        );
        ConversionAdmissionResult rejection = ConversionAdmissionResult.capacityRejected(
                Duration.ofSeconds(2)
        );

        assertTrue(capacity.saturated());
        assertEquals(ConversionAdmissionDecision.CAPACITY_REJECTED, rejection.decision());
        assertTrue(rejection.jobId().isEmpty());
        assertEquals(Duration.ofSeconds(2), rejection.retryAfter().orElseThrow());
    }

    @Test
    void retryRecoveryCancellationAndDeadLetterRemainGenerationBound() {
        UUID jobId = UUID.randomUUID();
        long generation = 7L;
        UUID staleLeaseId = UUID.randomUUID();
        Instant recoveredAt = Instant.parse("2026-08-20T13:20:00Z");
        Instant retryAt = recoveredAt.plusSeconds(10);

        ConversionRecoveryRecord recovery = ConversionRecoveryRecord.recover(
                UUID.randomUUID(),
                jobId,
                generation,
                1,
                staleLeaseId,
                recoveredAt,
                retryAt
        );
        ConversionRetryRecord retry = ConversionRetryRecord.schedule(
                UUID.randomUUID(),
                jobId,
                generation,
                2,
                retryAt
        );
        ConversionCancellationRecord cancellation = ConversionCancellationRecord.requested(
                UUID.randomUUID(),
                "tenant-a",
                jobId,
                generation,
                retryAt.plusSeconds(1)
        ).markCompleted(retryAt.plusSeconds(2));
        ConversionDeadLetterRecord deadLetter = ConversionDeadLetterRecord.record(
                UUID.randomUUID(),
                jobId,
                generation,
                2,
                retryAt.plusSeconds(3),
                "RETRY_EXHAUSTED"
        );

        assertTrue(recovery.authorizes(jobId, generation, 1, staleLeaseId));
        assertTrue(recovery.isEligibleAt(retryAt));
        assertTrue(retry.authorizes(jobId, generation, 2));
        assertTrue(retry.isDue(retryAt));
        assertEquals(
                ConversionCancellationState.COMPLETED_BEFORE_CANCELLATION,
                cancellation.state()
        );
        assertTrue(deadLetter.authorizes(jobId, generation, 2));
    }
}
