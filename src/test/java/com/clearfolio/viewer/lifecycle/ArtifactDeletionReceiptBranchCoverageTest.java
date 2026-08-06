package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Covers fail-closed receipt-state combinations at the boundaries of attempt
 * and failure evidence.
 */
class ArtifactDeletionReceiptBranchCoverageTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void failedReceiptWithoutAnyAttemptEvidenceIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> failedReceipt(0, null, REQUESTED_AT.plusSeconds(1), "artifact_store_delete_failed")
        );

        assertEquals("failed receipt fields are inconsistent", exception.getMessage());
    }

    @Test
    void failedReceiptRequiresAttemptTimeToEqualItsStateTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> failedReceipt(
                        1,
                        REQUESTED_AT.plusSeconds(1),
                        REQUESTED_AT.plusSeconds(2),
                        "artifact_store_delete_failed"
                )
        );

        assertEquals("failed receipt fields are inconsistent", exception.getMessage());
    }

    @Test
    void failedReceiptAcceptsCompleteMatchingAttemptEvidence() {
        Instant failedAt = REQUESTED_AT.plusSeconds(2);

        ArtifactDeletionReceipt receipt = failedReceipt(
                1,
                failedAt,
                failedAt,
                "artifact_store_delete_failed"
        );

        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, receipt.state());
        assertEquals(failedAt, receipt.lastAttemptAt());
    }

    @Test
    void completedReceiptRejectsFailureEvidence() {
        Instant completedAt = REQUESTED_AT.plusSeconds(2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactDeletionReceipt(
                        UUID.fromString("11111111-2222-3333-4444-555555555555"),
                        "tenant-edge",
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "audit-v1:0123456789abcdef0123456789abcdef",
                        REQUESTED_AT,
                        completedAt,
                        ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                        0,
                        null,
                        completedAt,
                        "artifact_store_delete_failed"
                )
        );

        assertEquals("completed receipt fields are inconsistent", exception.getMessage());
    }

    private static ArtifactDeletionReceipt failedReceipt(
            int attemptCount,
            Instant lastAttemptAt,
            Instant stateChangedAt,
            String failureCode
    ) {
        return new ArtifactDeletionReceipt(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "tenant-edge",
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "audit-v1:0123456789abcdef0123456789abcdef",
                REQUESTED_AT,
                stateChangedAt,
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                attemptCount,
                lastAttemptAt,
                null,
                failureCode
        );
    }
}
