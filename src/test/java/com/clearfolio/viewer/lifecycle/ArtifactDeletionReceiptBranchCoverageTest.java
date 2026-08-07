package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Covers fail-closed receipt-state combinations at checksum, attempt, and
 * failure-evidence boundaries.
 */
class ArtifactDeletionReceiptBranchCoverageTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID JOB_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-edge";
    private static final String CHECKSUM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String AUDIT_ID =
            "audit-v1:0123456789abcdef0123456789abcdef";
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
    void failedReceiptRequiresAStableFailureCode() {
        Instant failedAt = REQUESTED_AT.plusSeconds(2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> failedReceipt(1, failedAt, failedAt, null)
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
                        REQUEST_ID,
                        TENANT_ID,
                        JOB_ID,
                        CHECKSUM,
                        AUDIT_ID,
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

    @Test
    void exactChecksumCannotBeCapturedTwice() {
        ArtifactDeletionReceipt pending = requestedReceipt(
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                ArtifactDeletionState.DELETION_REQUESTED
        );
        ArtifactDeletionReceipt captured = pending.captureArtifactChecksum(
                CHECKSUM,
                REQUESTED_AT.plusSeconds(1)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> captured.captureArtifactChecksum(CHECKSUM, REQUESTED_AT.plusSeconds(2))
        );

        assertEquals("invalid artifact deletion transition", exception.getMessage());
    }

    @Test
    void metadataCannotBeTombstonedWhileChecksumIsPending() {
        ArtifactDeletionReceipt pending = requestedReceipt(
                ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                ArtifactDeletionState.DELETION_REQUESTED
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> pending.markMetadataTombstoned(REQUESTED_AT.plusSeconds(1))
        );

        assertEquals("invalid artifact deletion transition", exception.getMessage());
    }

    @Test
    void pendingChecksumIsRejectedAfterMetadataTombstoning() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> requestedReceipt(
                        ArtifactDeletionReceipt.PENDING_ARTIFACT_CHECKSUM,
                        ArtifactDeletionState.METADATA_TOMBSTONED
                )
        );

        assertEquals(
                "pending artifact checksum is only valid before metadata tombstoning",
                exception.getMessage()
        );
    }

    private static ArtifactDeletionReceipt failedReceipt(
            int attemptCount,
            Instant lastAttemptAt,
            Instant stateChangedAt,
            String failureCode
    ) {
        return new ArtifactDeletionReceipt(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                CHECKSUM,
                AUDIT_ID,
                REQUESTED_AT,
                stateChangedAt,
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                attemptCount,
                lastAttemptAt,
                null,
                failureCode
        );
    }

    private static ArtifactDeletionReceipt requestedReceipt(
            String checksum,
            ArtifactDeletionState state
    ) {
        return new ArtifactDeletionReceipt(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                checksum,
                AUDIT_ID,
                REQUESTED_AT,
                REQUESTED_AT,
                state,
                0,
                null,
                null,
                null
        );
    }
}
