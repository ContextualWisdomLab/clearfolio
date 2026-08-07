package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Exercises the fail-closed durable snapshot-failure contract and every
 * defensive replay-detector condition.
 */
class ArtifactDeletionSnapshotFailureCoverageTest {

    private static final Instant START = Instant.parse("2026-08-07T01:00:00Z");
    private static final UUID REQUEST_ID =
            UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID =
            UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final String TENANT_ID = "tenant-snapshot";
    private static final String AUDIT_CORRELATION_ID = "cleanup-v1:snapshot";
    private static final String ARTIFACT_CHECKSUM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void ledgerRejectsUnknownFailureCodesAndPersistsRetryEvidence() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();

        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.recordSnapshotFailure(JOB_ID, "unknown_failure", START)
        );
        assertThrows(
                IllegalStateException.class,
                () -> ledger.recordSnapshotFailure(
                        JOB_ID,
                        ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE,
                        START
                )
        );

        ledger.request(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                AUDIT_CORRELATION_ID,
                START
        );

        ArtifactDeletionReceipt failed = ledger.recordSnapshotFailure(
                JOB_ID,
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE,
                START.plusSeconds(1)
        );

        assertEquals(ArtifactDeletionState.DELETION_REQUESTED, failed.state());
        assertEquals(1, failed.attemptCount());
        assertEquals(START.plusSeconds(1), failed.lastAttemptAt());
        assertEquals(START.plusSeconds(1), failed.stateChangedAt());
        assertEquals(ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE, failed.failureCode());
        assertNotNull(ledger.findByJobId(JOB_ID).orElseThrow());
    }

    @Test
    void receiptRejectsSnapshotFailuresAfterBindingAndInconsistentPendingEvidence() {
        ArtifactDeletionReceipt exactDigest = receipt(
                ARTIFACT_CHECKSUM,
                START,
                0,
                null,
                null
        );

        assertThrows(
                IllegalStateException.class,
                () -> exactDigest.recordArtifactSnapshotFailure(START.plusSeconds(1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                        START.plusSeconds(2),
                        1,
                        START.plusSeconds(1),
                        ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                        START,
                        0,
                        null,
                        ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
                )
        );
    }

    @Test
    void initialRequestDetectorCoversEveryFailClosedCondition() throws Exception {
        Method detector = ArtifactDeletionLedger.class.getDeclaredMethod(
                "isInitialRequest",
                ArtifactDeletionReceipt.class
        );
        detector.setAccessible(true);

        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.METADATA_TOMBSTONED,
                0,
                null,
                null,
                null,
                START,
                START
        )));
        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                1,
                null,
                null,
                null,
                START,
                START
        )));
        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                START,
                null,
                null,
                START,
                START
        )));
        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                START,
                null,
                START,
                START
        )));
        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE,
                START,
                START
        )));
        assertFalse(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                null,
                START.plusSeconds(1),
                START
        )));
        assertTrue(isInitialRequest(detector, initialCandidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                null,
                START,
                START
        )));
    }

    @Test
    void snapshotFailureDetectorCoversEveryFailClosedCondition() throws Exception {
        Method detector = ArtifactDeletionLedger.class.getDeclaredMethod(
                "isSnapshotFailureTransition",
                ArtifactDeletionReceipt.class,
                ArtifactDeletionReceipt.class
        );
        detector.setAccessible(true);

        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.METADATA_TOMBSTONED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.METADATA_TOMBSTONED,
                true,
                true,
                true,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                false,
                true,
                true,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                false,
                true,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                false,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                0,
                2,
                START.plusSeconds(1),
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                0,
                1,
                null,
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                0,
                1,
                START,
                START.plusSeconds(1),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
        assertFalse(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                0,
                1,
                START.plusSeconds(1),
                START.plusSeconds(1),
                "different_failure"
        )));
        assertTrue(detect(detector, candidate(
                ArtifactDeletionState.DELETION_REQUESTED,
                ArtifactDeletionState.DELETION_REQUESTED,
                true,
                true,
                true,
                1,
                2,
                START.plusSeconds(2),
                START.plusSeconds(2),
                ArtifactDeletionReceipt.SNAPSHOT_READ_FAILURE_CODE
        )));
    }

    private static ArtifactDeletionReceipt receipt(
            String checksum,
            Instant stateChangedAt,
            int attemptCount,
            Instant lastAttemptAt,
            String failureCode
    ) {
        return new ArtifactDeletionReceipt(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                checksum,
                AUDIT_CORRELATION_ID,
                START,
                stateChangedAt,
                ArtifactDeletionState.DELETION_REQUESTED,
                attemptCount,
                lastAttemptAt,
                null,
                failureCode
        );
    }

    private static ArtifactDeletionReceipt initialCandidate(
            ArtifactDeletionState state,
            int attemptCount,
            Instant lastAttemptAt,
            Instant completedAt,
            String failureCode,
            Instant stateChangedAt,
            Instant requestedAt
    ) {
        ArtifactDeletionReceipt receipt = mock(ArtifactDeletionReceipt.class);
        when(receipt.state()).thenReturn(state);
        when(receipt.attemptCount()).thenReturn(attemptCount);
        when(receipt.lastAttemptAt()).thenReturn(lastAttemptAt);
        when(receipt.completedAt()).thenReturn(completedAt);
        when(receipt.failureCode()).thenReturn(failureCode);
        when(receipt.stateChangedAt()).thenReturn(stateChangedAt);
        when(receipt.requestedAt()).thenReturn(requestedAt);
        return receipt;
    }

    private static boolean isInitialRequest(Method detector, ArtifactDeletionReceipt receipt)
            throws Exception {
        return (boolean) detector.invoke(null, receipt);
    }

    private static SnapshotCandidate candidate(
            ArtifactDeletionState currentState,
            ArtifactDeletionState replayedState,
            boolean currentPending,
            boolean replayedPending,
            boolean sameRequestIdentity,
            int currentAttempts,
            int replayedAttempts,
            Instant replayedLastAttemptAt,
            Instant replayedStateChangedAt,
            String replayedFailureCode
    ) {
        ArtifactDeletionReceipt current = mock(ArtifactDeletionReceipt.class);
        ArtifactDeletionReceipt replayed = mock(ArtifactDeletionReceipt.class);
        when(current.state()).thenReturn(currentState);
        when(replayed.state()).thenReturn(replayedState);
        when(current.isArtifactChecksumPending()).thenReturn(currentPending);
        when(replayed.isArtifactChecksumPending()).thenReturn(replayedPending);
        when(current.hasSameRequestIdentity(replayed)).thenReturn(sameRequestIdentity);
        when(current.attemptCount()).thenReturn(currentAttempts);
        when(replayed.attemptCount()).thenReturn(replayedAttempts);
        when(replayed.lastAttemptAt()).thenReturn(replayedLastAttemptAt);
        when(replayed.stateChangedAt()).thenReturn(replayedStateChangedAt);
        when(replayed.failureCode()).thenReturn(replayedFailureCode);
        return new SnapshotCandidate(current, replayed);
    }

    private static boolean detect(Method detector, SnapshotCandidate candidate) throws Exception {
        return (boolean) detector.invoke(null, candidate.current(), candidate.replayed());
    }

    private record SnapshotCandidate(
            ArtifactDeletionReceipt current,
            ArtifactDeletionReceipt replayed
    ) {
    }
}
