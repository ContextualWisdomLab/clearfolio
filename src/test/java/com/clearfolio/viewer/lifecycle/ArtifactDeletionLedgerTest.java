package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies durable, idempotent, monotonic artifact-deletion receipt evidence.
 */
class ArtifactDeletionLedgerTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-north";
    private static final String ARTIFACT_CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String AUDIT_CORRELATION_ID = "audit-v1:0123456789abcdef0123456789abcdef";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T00:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void createsAndFindsRequestedReceipt() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();

        ArtifactDeletionReceipt receipt = request(ledger);

        assertEquals(REQUEST_ID, receipt.requestId());
        assertEquals(TENANT_ID, receipt.tenantId());
        assertEquals(JOB_ID, receipt.jobId());
        assertEquals(ARTIFACT_CHECKSUM, receipt.artifactChecksum());
        assertEquals(AUDIT_CORRELATION_ID, receipt.auditCorrelationId());
        assertEquals(REQUESTED_AT, receipt.requestedAt());
        assertEquals(REQUESTED_AT, receipt.stateChangedAt());
        assertEquals(ArtifactDeletionState.DELETION_REQUESTED, receipt.state());
        assertEquals(0, receipt.attemptCount());
        assertTrue(ledger.findByJobId(JOB_ID).isPresent());
        assertEquals(1, ledger.pendingCount());
    }

    @Test
    void identicalDeletionRequestIsIdempotent() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt original = request(ledger);

        ArtifactDeletionReceipt repeated = request(ledger);

        assertSame(original, repeated);
        assertEquals(1, ledger.pendingCount());
    }

    @Test
    void conflictingRequestForReservedJobFailsClosed() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        request(ledger);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ledger.request(
                        UUID.randomUUID(),
                        "tenant-south",
                        JOB_ID,
                        ARTIFACT_CHECKSUM,
                        "audit-v1:ffffffffffffffffffffffffffffffff",
                        REQUESTED_AT.plusSeconds(1)
                )
        );

        assertEquals("artifact deletion receipt conflicts with an existing lifecycle", exception.getMessage());
        assertEquals(TENANT_ID, ledger.findByJobId(JOB_ID).orElseThrow().tenantId());
    }

    @Test
    void stateTransitionsAreMonotonicAndCompletionIsTerminal() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        request(ledger);

        ArtifactDeletionReceipt tombstoned = ledger.markMetadataTombstoned(
                JOB_ID,
                REQUESTED_AT.plusSeconds(1)
        );
        ArtifactDeletionReceipt pending = ledger.markCleanupPending(
                JOB_ID,
                REQUESTED_AT.plusSeconds(2)
        );
        ArtifactDeletionReceipt completed = ledger.markCleanupCompleted(
                JOB_ID,
                REQUESTED_AT.plusSeconds(3)
        );

        assertEquals(ArtifactDeletionState.METADATA_TOMBSTONED, tombstoned.state());
        assertEquals(REQUESTED_AT.plusSeconds(1), tombstoned.stateChangedAt());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, pending.state());
        assertEquals(REQUESTED_AT.plusSeconds(2), pending.stateChangedAt());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completed.state());
        assertEquals(REQUESTED_AT.plusSeconds(3), completed.stateChangedAt());
        assertEquals(REQUESTED_AT.plusSeconds(3), completed.completedAt());
        assertEquals(0, ledger.pendingCount());
        assertThrows(
                IllegalStateException.class,
                () -> ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(4))
        );
    }

    @Test
    void transitionTimeCannotMoveBackward() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        request(ledger);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ledger.markMetadataTombstoned(JOB_ID, REQUESTED_AT.minusSeconds(1))
        );

        assertEquals("stateChangedAt must not precede the prior transition", exception.getMessage());
        assertEquals(ArtifactDeletionState.DELETION_REQUESTED, ledger.findByJobId(JOB_ID).orElseThrow().state());
    }

    @Test
    void failedCleanupCanBeRetriedAndAttemptsRemainDurable() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        request(ledger);
        ledger.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));

        ArtifactDeletionReceipt firstFailure = ledger.recordCleanupFailure(
                JOB_ID,
                "storage_unavailable",
                REQUESTED_AT.plusSeconds(3)
        );
        ArtifactDeletionReceipt retry = ledger.markCleanupPending(
                JOB_ID,
                REQUESTED_AT.plusSeconds(4)
        );
        ArtifactDeletionReceipt secondFailure = ledger.recordCleanupFailure(
                JOB_ID,
                "storage_unavailable",
                REQUESTED_AT.plusSeconds(5)
        );

        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, firstFailure.state());
        assertEquals(1, firstFailure.attemptCount());
        assertEquals("storage_unavailable", firstFailure.failureCode());
        assertEquals(REQUESTED_AT.plusSeconds(3), firstFailure.stateChangedAt());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, retry.state());
        assertEquals(1, retry.attemptCount());
        assertEquals(REQUESTED_AT.plusSeconds(4), retry.stateChangedAt());
        assertEquals(2, secondFailure.attemptCount());
        assertEquals(REQUESTED_AT.plusSeconds(5), secondFailure.lastAttemptAt());
        assertEquals(REQUESTED_AT.plusSeconds(5), secondFailure.stateChangedAt());
    }

    @Test
    void failureCodeMustBeControlledAndCannotCarryStorageDetails() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        request(ledger);
        ledger.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ledger.recordCleanupFailure(
                        JOB_ID,
                        "permission denied for /private/artifacts/" + JOB_ID + ".pdf",
                        REQUESTED_AT.plusSeconds(3)
                )
        );

        assertEquals("failureCode must be a controlled code", exception.getMessage());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, ledger.findByJobId(JOB_ID).orElseThrow().state());
    }

    @Test
    void fileBackedLedgerReplaysTheLatestReceiptAfterRestart() {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        ArtifactDeletionLedger firstProcess = new ArtifactDeletionLedger(ledgerPath);
        request(firstProcess);
        firstProcess.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        firstProcess.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));
        firstProcess.recordCleanupFailure(JOB_ID, "storage_timeout", REQUESTED_AT.plusSeconds(3));

        ArtifactDeletionLedger restartedProcess = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionReceipt restored = restartedProcess.findByJobId(JOB_ID).orElseThrow();

        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, restored.state());
        assertEquals(1, restored.attemptCount());
        assertEquals("storage_timeout", restored.failureCode());
        assertEquals(REQUESTED_AT.plusSeconds(3), restored.stateChangedAt());
        assertEquals(AUDIT_CORRELATION_ID, restored.auditCorrelationId());
        assertEquals(List.of(restored), restartedProcess.pendingReceipts());
        assertSame(restored, request(restartedProcess));
    }

    @Test
    void completedReceiptsAreNotReturnedAsPendingAfterRestart() {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        ArtifactDeletionLedger firstProcess = new ArtifactDeletionLedger(ledgerPath);
        request(firstProcess);
        firstProcess.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        firstProcess.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));
        firstProcess.markCleanupCompleted(JOB_ID, REQUESTED_AT.plusSeconds(3));

        ArtifactDeletionLedger restartedProcess = new ArtifactDeletionLedger(ledgerPath);

        assertTrue(restartedProcess.pendingReceipts().isEmpty());
        assertEquals(0, restartedProcess.pendingCount());
        assertTrue(restartedProcess.findByJobId(JOB_ID).orElseThrow().isCompleted());
    }

    @Test
    void malformedLedgerInputFailsClosed() throws IOException {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        Files.writeString(ledgerPath, "not-a-valid-ledger-line\n", StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ArtifactDeletionLedger(ledgerPath)
        );

        assertEquals("artifact deletion ledger contains an invalid line", exception.getMessage());
    }

    @Test
    void oversizedLedgerLineFailsClosed() throws IOException {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        Files.writeString(ledgerPath, "x".repeat(ArtifactDeletionLedger.MAX_LEDGER_LINE_BYTES + 1), StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ArtifactDeletionLedger(ledgerPath)
        );

        assertEquals("artifact deletion ledger line exceeds the configured bound", exception.getMessage());
    }

    @Test
    void missingReceiptAndInvalidTransitionFailClosed() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();

        assertTrue(ledger.findByJobId(null).isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> ledger.markCleanupCompleted(JOB_ID, REQUESTED_AT)
        );

        request(ledger);
        assertThrows(
                IllegalStateException.class,
                () -> ledger.markCleanupCompleted(JOB_ID, REQUESTED_AT.plusSeconds(1))
        );
    }

    @Test
    void receiptValidationRejectsInvalidIdentityChecksumAndAttempts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(" ", ARTIFACT_CHECKSUM, AUDIT_CORRELATION_ID, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(TENANT_ID, "not-a-sha256", AUDIT_CORRELATION_ID, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(TENANT_ID, ARTIFACT_CHECKSUM, " ", 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receipt(TENANT_ID, ARTIFACT_CHECKSUM, AUDIT_CORRELATION_ID, -1)
        );
    }

    private static ArtifactDeletionReceipt receipt(
            String tenantId,
            String artifactChecksum,
            String auditCorrelationId,
            int attemptCount
    ) {
        return new ArtifactDeletionReceipt(
                REQUEST_ID,
                tenantId,
                JOB_ID,
                artifactChecksum,
                auditCorrelationId,
                REQUESTED_AT,
                REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED,
                attemptCount,
                null,
                null,
                null
        );
    }

    private static ArtifactDeletionReceipt request(ArtifactDeletionLedger ledger) {
        return ledger.request(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                ARTIFACT_CHECKSUM,
                AUDIT_CORRELATION_ID,
                REQUESTED_AT
        );
    }
}
