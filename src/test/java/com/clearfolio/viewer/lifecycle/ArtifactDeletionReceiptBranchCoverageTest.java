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

    @Test
    void failedReceiptWithoutAnyAttemptEvidenceIsRejected() {
        Instant requestedAt = Instant.parse("2026-08-06T12:00:00Z");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactDeletionReceipt(
                        UUID.fromString("11111111-2222-3333-4444-555555555555"),
                        "tenant-edge",
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "audit-v1:0123456789abcdef0123456789abcdef",
                        requestedAt,
                        requestedAt.plusSeconds(1),
                        ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                        0,
                        null,
                        null,
                        "artifact_store_delete_failed"
                )
        );

        assertEquals("failed receipt fields are inconsistent", exception.getMessage());
    }
}
