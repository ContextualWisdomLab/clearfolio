package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises fail-closed receipt validation and append-only replay branches.
 */
class ArtifactDeletionLedgerCoverageTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-north";
    private static final String CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String AUDIT_ID = "audit-v1:0123456789abcdef0123456789abcdef";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T00:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void stringConfigurationSupportsNullBlankAndFileBackedModes() {
        ArtifactDeletionLedger nullConfigured = new ArtifactDeletionLedger((String) null);
        ArtifactDeletionLedger blankConfigured = new ArtifactDeletionLedger("  ");
        Path configuredPath = tempDirectory.resolve("configured_receipts.log");
        ArtifactDeletionLedger fileConfigured = new ArtifactDeletionLedger(configuredPath.toString());

        request(nullConfigured, JOB_ID, REQUEST_ID, TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        request(blankConfigured, UUID.randomUUID(), UUID.randomUUID(), TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        request(fileConfigured, UUID.randomUUID(), UUID.randomUUID(), TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);

        assertFalse(nullConfigured.findByJobId(UUID.randomUUID()).isPresent());
        assertEquals(1, blankConfigured.pendingCount());
        assertTrue(Files.exists(configuredPath));
    }

    @Test
    void pendingReceiptsAreSortedByRequestTimeAndJobIdentifier() {
        ArtifactDeletionReceiptStore store = new ArtifactDeletionLedger();
        UUID laterJob = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID earlierJob = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sameTimeEarlierJob = UUID.fromString("00000000-0000-0000-0000-000000000000");
        store.request(UUID.randomUUID(), TENANT_ID, laterJob, CHECKSUM, AUDIT_ID, REQUESTED_AT.plusSeconds(1));
        store.request(UUID.randomUUID(), TENANT_ID, earlierJob, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        store.request(UUID.randomUUID(), TENANT_ID, sameTimeEarlierJob, CHECKSUM, AUDIT_ID, REQUESTED_AT);

        assertEquals(
                List.of(sameTimeEarlierJob, earlierJob, laterJob),
                store.pendingReceipts().stream().map(ArtifactDeletionReceipt::jobId).toList()
        );
    }

    @Test
    void immutableIdentityComparisonRejectsEveryChangedField() {
        ArtifactDeletionReceipt baseline = receipt(
                REQUEST_ID,
                TENANT_ID,
                JOB_ID,
                CHECKSUM,
                AUDIT_ID,
                REQUESTED_AT,
                REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                null
        );

        assertFalse(baseline.hasSameIdentity(null));
        assertFalse(baseline.hasSameIdentity(receipt(UUID.randomUUID(), TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT, ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
        assertFalse(baseline.hasSameIdentity(receipt(REQUEST_ID, "tenant-south", JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT, ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
        assertFalse(baseline.hasSameIdentity(receipt(REQUEST_ID, TENANT_ID, UUID.randomUUID(), CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT, ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
        assertFalse(baseline.hasSameIdentity(receipt(REQUEST_ID, TENANT_ID, JOB_ID,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT, ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
        assertFalse(baseline.hasSameIdentity(receipt(REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM,
                "audit-v1:ffffffffffffffffffffffffffffffff", REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
        assertFalse(baseline.hasSameIdentity(receipt(REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT.plusSeconds(1), REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null)));
    }

    @Test
    void receiptConstructorAcceptsChecksumCaptureAndRejectsInconsistentStateEvidence() {
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.minusSeconds(1),
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
        ));
        assertEquals(
                REQUESTED_AT.plusSeconds(1),
                receipt(
                        REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                        REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                        ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
                ).stateChangedAt()
        );
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0,
                REQUESTED_AT.minusSeconds(1), null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.METADATA_TOMBSTONED, 0,
                null, REQUESTED_AT, null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, 0,
                null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, 0,
                null, REQUESTED_AT.plusSeconds(2), null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, 0,
                null, REQUESTED_AT.plusSeconds(1), "storage_failed"
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, 1,
                null, null, "storage_failed"
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(2),
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, 1,
                REQUESTED_AT.plusSeconds(1), null, "storage_failed"
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT.plusSeconds(1),
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, 0,
                REQUESTED_AT.plusSeconds(1), null, "storage_failed"
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.METADATA_TOMBSTONED, 0,
                null, null, "storage_failed"
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, "t".repeat(257), JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, "a".repeat(257),
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM.toUpperCase(), AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
        ));
    }

    @Test
    void transitionMethodsRejectWrongStateAndNullTime() {
        ArtifactDeletionReceipt requested = receipt(
                REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID,
                REQUESTED_AT, REQUESTED_AT,
                ArtifactDeletionState.DELETION_REQUESTED, 0, null, null, null
        );

        assertThrows(NullPointerException.class, () -> requested.markMetadataTombstoned(null));
        assertThrows(IllegalStateException.class, () -> requested.markCleanupPending(REQUESTED_AT));
        assertThrows(IllegalStateException.class, () -> requested.recordCleanupFailure("storage_failed", REQUESTED_AT));
        assertThrows(IllegalStateException.class, () -> requested.markCleanupCompleted(REQUESTED_AT));
        assertEquals(REQUESTED_AT, requested.markMetadataTombstoned(REQUESTED_AT).stateChangedAt());
    }

    @Test
    void strictUtf8CrLfAndBlankLineReplayAreHandledExplicitly() throws IOException {
        Path source = tempDirectory.resolve("source.log");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger(source);
        request(ledger, JOB_ID, REQUEST_ID, TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        String sourceText = Files.readString(source, StandardCharsets.UTF_8);

        Path crlf = tempDirectory.resolve("crlf.log");
        Files.writeString(crlf, sourceText.replace("\n", "\r\n"), StandardCharsets.UTF_8);
        assertTrue(new ArtifactDeletionLedger(crlf).findByJobId(JOB_ID).isPresent());

        Path blank = tempDirectory.resolve("blank.log");
        Files.writeString(blank, "\n", StandardCharsets.UTF_8);
        assertEquals(
                "artifact deletion ledger contains an invalid line",
                assertThrows(IllegalStateException.class, () -> new ArtifactDeletionLedger(blank)).getMessage()
        );

        Path invalidUtf8 = tempDirectory.resolve("invalid_utf8.log");
        Files.write(invalidUtf8, new byte[] {(byte) 0xC3, 0x28, '\n'});
        assertEquals(
                "artifact deletion ledger contains an invalid line",
                assertThrows(IllegalStateException.class, () -> new ArtifactDeletionLedger(invalidUtf8)).getMessage()
        );
    }

    @Test
    void loadAndWriteIoFailuresAreReportedWithoutPartialSuccess() throws IOException {
        Path directoryInsteadOfLedger = tempDirectory.resolve("ledger_directory");
        Files.createDirectory(directoryInsteadOfLedger);
        assertEquals(
                "artifact deletion ledger cannot be loaded",
                assertThrows(
                        IllegalStateException.class,
                        () -> new ArtifactDeletionLedger(directoryInsteadOfLedger)
                ).getMessage()
        );

        Path parentDirectory = tempDirectory.resolve("write_parent");
        Files.createDirectory(parentDirectory);
        Path ledgerPath = parentDirectory.resolve("ledger.log");
        ArtifactDeletionLedger unwritable = new ArtifactDeletionLedger(ledgerPath);
        Files.delete(parentDirectory);
        Files.writeString(parentDirectory, "not a directory", StandardCharsets.UTF_8);
        assertEquals(
                "artifact deletion ledger cannot be written",
                assertThrows(
                        IllegalStateException.class,
                        () -> request(unwritable, JOB_ID, REQUEST_ID, TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT)
                ).getMessage()
        );
        assertTrue(unwritable.findByJobId(JOB_ID).isEmpty());
    }

    @Test
    void replayRejectsInvalidFirstRecordIdentityAndTransitionSequences() throws IOException {
        Path valid = tempDirectory.resolve("valid_sequence.log");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger(valid);
        request(ledger, JOB_ID, REQUEST_ID, TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        ledger.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));
        ledger.markCleanupCompleted(JOB_ID, REQUESTED_AT.plusSeconds(3));
        List<String> lines = Files.readAllLines(valid, StandardCharsets.UTF_8);

        Path nonRequestedFirst = tempDirectory.resolve("non_requested_first.log");
        Files.write(nonRequestedFirst, lines.subList(1, 2), StandardCharsets.UTF_8);
        assertInvalidLedger(nonRequestedFirst);

        Path nonZeroFirstAttempt = tempDirectory.resolve("nonzero_first_attempt.log");
        String[] requestedFields = fields(lines.getFirst());
        requestedFields[9] = "1";
        Files.writeString(nonZeroFirstAttempt, String.join("\t", requestedFields), StandardCharsets.UTF_8);
        assertInvalidLedger(nonZeroFirstAttempt);

        Path completedThenPending = tempDirectory.resolve("completed_then_pending.log");
        List<String> invalidTransitionLines = new ArrayList<>(lines);
        invalidTransitionLines.add(lines.get(2));
        Files.write(completedThenPending, invalidTransitionLines, StandardCharsets.UTF_8);
        assertInvalidLedger(completedThenPending);

        Path nonMonotonic = tempDirectory.resolve("non_monotonic.log");
        List<String> nonMonotonicLines = new ArrayList<>(lines.subList(0, 3));
        String[] pendingFields = fields(nonMonotonicLines.get(2));
        pendingFields[7] = REQUESTED_AT.toString();
        nonMonotonicLines.set(2, String.join("\t", pendingFields));
        Files.write(nonMonotonic, nonMonotonicLines, StandardCharsets.UTF_8);
        assertInvalidLedger(nonMonotonic);
    }

    @Test
    void replayRejectsIdentityConflictInvalidBase64AndMalformedRequiredFields() throws IOException {
        Path north = tempDirectory.resolve("north.log");
        request(new ArtifactDeletionLedger(north), JOB_ID, REQUEST_ID, TENANT_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        Path south = tempDirectory.resolve("south.log");
        request(
                new ArtifactDeletionLedger(south),
                JOB_ID,
                UUID.randomUUID(),
                "tenant-south",
                CHECKSUM,
                "audit-v1:ffffffffffffffffffffffffffffffff",
                REQUESTED_AT.plusSeconds(1)
        );

        Path conflict = tempDirectory.resolve("identity_conflict.log");
        List<String> conflictLines = new ArrayList<>(Files.readAllLines(north, StandardCharsets.UTF_8));
        conflictLines.addAll(Files.readAllLines(south, StandardCharsets.UTF_8));
        Files.write(conflict, conflictLines, StandardCharsets.UTF_8);
        assertInvalidLedger(conflict);

        String[] invalidBase64Fields = fields(Files.readAllLines(north, StandardCharsets.UTF_8).getFirst());
        invalidBase64Fields[2] = "*";
        Path invalidBase64 = tempDirectory.resolve("invalid_base64.log");
        Files.writeString(invalidBase64, String.join("\t", invalidBase64Fields), StandardCharsets.UTF_8);
        assertInvalidLedger(invalidBase64);

        String[] absentTenantFields = fields(Files.readAllLines(north, StandardCharsets.UTF_8).getFirst());
        absentTenantFields[2] = "-";
        Path absentTenant = tempDirectory.resolve("absent_tenant.log");
        Files.writeString(absentTenant, String.join("\t", absentTenantFields), StandardCharsets.UTF_8);
        assertInvalidLedger(absentTenant);
    }

    private static void assertInvalidLedger(Path path) {
        assertEquals(
                "artifact deletion ledger contains an invalid line",
                assertThrows(IllegalStateException.class, () -> new ArtifactDeletionLedger(path)).getMessage()
        );
    }

    private static String[] fields(String line) {
        return line.split("\t", -1);
    }

    private static ArtifactDeletionReceipt request(
            ArtifactDeletionReceiptStore store,
            UUID jobId,
            UUID requestId,
            String tenantId,
            String checksum,
            String auditId,
            Instant requestedAt
    ) {
        return store.request(requestId, tenantId, jobId, checksum, auditId, requestedAt);
    }

    private static ArtifactDeletionReceipt receipt(
            UUID requestId,
            String tenantId,
            UUID jobId,
            String checksum,
            String auditId,
            Instant requestedAt,
            Instant stateChangedAt,
            ArtifactDeletionState state,
            int attemptCount,
            Instant lastAttemptAt,
            Instant completedAt,
            String failureCode
    ) {
        return new ArtifactDeletionReceipt(
                requestId,
                tenantId,
                jobId,
                checksum,
                auditId,
                requestedAt,
                stateChangedAt,
                state,
                attemptCount,
                lastAttemptAt,
                completedAt,
                failureCode
        );
    }
}
