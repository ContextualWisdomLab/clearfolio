package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that restart replay accepts only complete, internally consistent
 * deletion-lifecycle evidence.
 */
class ArtifactDeletionLedgerReplayValidationTest {

    private static final UUID REQUEST_ID = UUID.fromString("23232323-4545-6767-8989-010101010101");
    private static final UUID JOB_ID = UUID.fromString("bcbcbcbc-dede-fafa-2323-454545454545");
    private static final String TENANT_ID = "tenant-replay-validation";
    private static final String CHECKSUM = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    private static final String AUDIT_ID = "audit-v1:replay-validation";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T10:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void invalidUtf8LedgerBytesFailClosed() throws IOException {
        Path ledgerPath = ledgerPath();
        Files.write(ledgerPath, new byte[] {(byte) 0xc3, 0x28, '\n'});

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void whitespaceOnlyRequiredFieldFailsClosed() throws IOException {
        Path ledgerPath = ledgerPath();
        Files.writeString(
                ledgerPath,
                line(
                        "   ",
                        ArtifactDeletionState.DELETION_REQUESTED,
                        REQUESTED_AT,
                        0,
                        null,
                        null,
                        null
                ),
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void immutableIdentityConflictFailsClosed() throws IOException {
        Path ledgerPath = ledgerPath();
        String requested = line(
                TENANT_ID,
                ArtifactDeletionState.DELETION_REQUESTED,
                REQUESTED_AT,
                0,
                null,
                null,
                null
        );
        String conflicting = line(
                "tenant-conflicting-owner",
                ArtifactDeletionState.METADATA_TOMBSTONED,
                REQUESTED_AT.plusSeconds(1),
                0,
                null,
                null,
                null
        );
        Files.writeString(ledgerPath, requested + conflicting, StandardCharsets.UTF_8);

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void nonMonotonicTransitionTimeFailsClosed() throws IOException {
        Path ledgerPath = ledgerPath();
        String requested = line(
                TENANT_ID,
                ArtifactDeletionState.DELETION_REQUESTED,
                REQUESTED_AT,
                0,
                null,
                null,
                null
        );
        String tombstoned = line(
                TENANT_ID,
                ArtifactDeletionState.METADATA_TOMBSTONED,
                REQUESTED_AT.plusSeconds(3),
                0,
                null,
                null,
                null
        );
        String pendingEarlier = line(
                TENANT_ID,
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                REQUESTED_AT.plusSeconds(2),
                0,
                null,
                null,
                null
        );
        Files.writeString(
                ledgerPath,
                requested + tombstoned + pendingEarlier,
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void illegalSubsequentStateFailsClosed() throws IOException {
        Path ledgerPath = ledgerPath();
        String requested = line(
                TENANT_ID,
                ArtifactDeletionState.DELETION_REQUESTED,
                REQUESTED_AT,
                0,
                null,
                null,
                null
        );
        String pendingWithoutTombstone = line(
                TENANT_ID,
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                REQUESTED_AT.plusSeconds(1),
                0,
                null,
                null,
                null
        );
        Files.writeString(
                ledgerPath,
                requested + pendingWithoutTombstone,
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void stateSnapshotsRejectImpossibleAttemptEvidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionState.DELETION_REQUESTED,
                        REQUESTED_AT,
                        1,
                        REQUESTED_AT,
                        null,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionState.METADATA_TOMBSTONED,
                        REQUESTED_AT.plusSeconds(1),
                        1,
                        REQUESTED_AT,
                        null,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                        REQUESTED_AT.plusSeconds(2),
                        1,
                        REQUESTED_AT.plusSeconds(3),
                        null,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(
                        ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                        REQUESTED_AT.plusSeconds(2),
                        0,
                        null,
                        null,
                        "storage_timeout"
                )
        );
    }

    @Test
    void requestedStateCannotPairZeroAttemptsWithAttemptTime() throws IOException {
        Path ledgerPath = ledgerPath();
        Files.writeString(
                ledgerPath,
                line(
                        TENANT_ID,
                        ArtifactDeletionState.DELETION_REQUESTED,
                        REQUESTED_AT,
                        0,
                        REQUESTED_AT,
                        null,
                        null
                ),
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void retriedPendingStateMustPreserveLastAttemptEvidence() throws IOException {
        Path ledgerPath = ledgerPath();
        Instant tombstonedAt = REQUESTED_AT.plusSeconds(1);
        Instant firstPendingAt = REQUESTED_AT.plusSeconds(2);
        Instant failedAt = REQUESTED_AT.plusSeconds(3);
        Instant retryAt = REQUESTED_AT.plusSeconds(4);
        String requested = line(
                TENANT_ID,
                ArtifactDeletionState.DELETION_REQUESTED,
                REQUESTED_AT,
                0,
                null,
                null,
                null
        );
        String tombstoned = line(
                TENANT_ID,
                ArtifactDeletionState.METADATA_TOMBSTONED,
                tombstonedAt,
                0,
                null,
                null,
                null
        );
        String pending = line(
                TENANT_ID,
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                firstPendingAt,
                0,
                null,
                null,
                null
        );
        String failed = line(
                TENANT_ID,
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                failedAt,
                1,
                failedAt,
                null,
                "storage_timeout"
        );
        String retryWithRewrittenAttemptTime = line(
                TENANT_ID,
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                retryAt,
                1,
                firstPendingAt,
                null,
                null
        );
        Files.writeString(
                ledgerPath,
                requested + tombstoned + pending + failed + retryWithRewrittenAttemptTime,
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void retriedPendingStateMustPreserveAttemptCount() throws IOException {
        Path ledgerPath = ledgerPath();
        Instant tombstonedAt = REQUESTED_AT.plusSeconds(1);
        Instant pendingAt = REQUESTED_AT.plusSeconds(2);
        Instant failedAt = REQUESTED_AT.plusSeconds(3);
        Instant retryAt = REQUESTED_AT.plusSeconds(4);
        Files.writeString(
                ledgerPath,
                line(TENANT_ID, ArtifactDeletionState.DELETION_REQUESTED, REQUESTED_AT, 0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.METADATA_TOMBSTONED, tombstonedAt,
                                0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, pendingAt,
                                0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, failedAt,
                                1, failedAt, null, "storage_timeout")
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, retryAt,
                                2, failedAt, null, null),
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void firstPendingStateMustNotInventAttemptEvidence() throws IOException {
        Path ledgerPath = ledgerPath();
        Instant tombstonedAt = REQUESTED_AT.plusSeconds(1);
        Instant pendingAt = REQUESTED_AT.plusSeconds(2);
        Files.writeString(
                ledgerPath,
                line(TENANT_ID, ArtifactDeletionState.DELETION_REQUESTED, REQUESTED_AT,
                        0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.METADATA_TOMBSTONED, tombstonedAt,
                                0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, pendingAt,
                                1, tombstonedAt, null, null),
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    @Test
    void completedReceiptCannotTransitionAgainDuringReplay() throws IOException {
        Path ledgerPath = ledgerPath();
        Instant tombstonedAt = REQUESTED_AT.plusSeconds(1);
        Instant pendingAt = REQUESTED_AT.plusSeconds(2);
        Instant completedAt = REQUESTED_AT.plusSeconds(3);
        Instant duplicateCompletedAt = REQUESTED_AT.plusSeconds(4);
        Files.writeString(
                ledgerPath,
                line(TENANT_ID, ArtifactDeletionState.DELETION_REQUESTED, REQUESTED_AT,
                        0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.METADATA_TOMBSTONED, tombstonedAt,
                                0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, pendingAt,
                                0, null, null, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completedAt,
                                0, null, completedAt, null)
                        + line(TENANT_ID, ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, duplicateCompletedAt,
                                0, null, duplicateCompletedAt, null),
                StandardCharsets.UTF_8
        );

        assertInvalidLedger(ledgerPath);
    }

    private Path ledgerPath() {
        return tempDirectory.resolve("artifact_deletion_receipt.log");
    }

    private static void assertInvalidLedger(Path ledgerPath) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ArtifactDeletionLedger(ledgerPath)
        );
        assertEquals("artifact deletion ledger contains an invalid line", exception.getMessage());
    }

    private static ArtifactDeletionReceipt receipt(
            ArtifactDeletionState state,
            Instant stateChangedAt,
            int attemptCount,
            Instant lastAttemptAt,
            Instant completedAt,
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
                state,
                attemptCount,
                lastAttemptAt,
                completedAt,
                failureCode
        );
    }

    private static String line(
            String tenantId,
            ArtifactDeletionState state,
            Instant stateChangedAt,
            int attemptCount,
            Instant lastAttemptAt,
            Instant completedAt,
            String failureCode
    ) {
        return String.join(
                "\t",
                "RECEIPT_V1",
                REQUEST_ID.toString(),
                encode(tenantId),
                JOB_ID.toString(),
                encode(CHECKSUM),
                encode(AUDIT_ID),
                REQUESTED_AT.toString(),
                stateChangedAt.toString(),
                state.name(),
                Integer.toString(attemptCount),
                optionalInstant(lastAttemptAt),
                optionalInstant(completedAt),
                failureCode == null ? "-" : encode(failureCode)
        ) + "\n";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String optionalInstant(Instant value) {
        return value == null ? "-" : value.toString();
    }
}
