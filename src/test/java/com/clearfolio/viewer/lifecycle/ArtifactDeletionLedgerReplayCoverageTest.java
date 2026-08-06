package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers receipt replay branches that protect immutable identity and monotonic
 * retry evidence.
 */
class ArtifactDeletionLedgerReplayCoverageTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-north";
    private static final String CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String AUDIT_ID = "audit-v1:0123456789abcdef0123456789abcdef";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T00:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void replayAcceptsCompleteFailureRetrySequenceAndRejectsWorkAfterCompletion() throws Exception {
        Path path = tempDirectory.resolve("retry_sequence.log");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger(path);
        ledger.request(REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        ledger.markMetadataTombstoned(JOB_ID, REQUESTED_AT.plusSeconds(1));
        ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(2));
        ledger.recordCleanupFailure(JOB_ID, "artifact_store_delete_failed", REQUESTED_AT.plusSeconds(3));
        ledger.markCleanupPending(JOB_ID, REQUESTED_AT.plusSeconds(4));
        ledger.markCleanupCompleted(JOB_ID, REQUESTED_AT.plusSeconds(5));

        ArtifactDeletionReceipt replayed = new ArtifactDeletionLedger(path)
                .findByJobId(JOB_ID)
                .orElseThrow();
        assertTrue(replayed.isCompleted());
        assertEquals(1, replayed.attemptCount());
        assertEquals(REQUESTED_AT.plusSeconds(3), replayed.lastAttemptAt());

        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        lines.add(lines.get(2));
        Files.write(path, lines, StandardCharsets.UTF_8);

        assertInvalidLedger(path);
    }

    @Test
    void replayRejectsWrongRecordVersionAndBlankRequiredTenant() throws Exception {
        Path source = tempDirectory.resolve("source.log");
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger(source);
        ledger.request(REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        String[] validFields = Files.readString(source, StandardCharsets.UTF_8)
                .stripTrailing()
                .split("\\t", -1);

        String[] wrongVersionFields = validFields.clone();
        wrongVersionFields[0] = "RECEIPT_V2";
        Path wrongVersion = tempDirectory.resolve("wrong_version.log");
        Files.writeString(
                wrongVersion,
                String.join("\t", wrongVersionFields) + "\n",
                StandardCharsets.UTF_8
        );
        assertInvalidLedger(wrongVersion);

        String[] blankTenantFields = validFields.clone();
        blankTenantFields[2] = "";
        Path blankTenant = tempDirectory.resolve("blank_tenant.log");
        Files.writeString(
                blankTenant,
                String.join("\t", blankTenantFields) + "\n",
                StandardCharsets.UTF_8
        );
        assertInvalidLedger(blankTenant);
    }

    @Test
    void failedReceiptRejectsLastAttemptTimeThatDiffersFromStateTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactDeletionReceipt(
                        REQUEST_ID,
                        TENANT_ID,
                        JOB_ID,
                        CHECKSUM,
                        AUDIT_ID,
                        REQUESTED_AT,
                        REQUESTED_AT.plusSeconds(3),
                        ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                        1,
                        REQUESTED_AT.plusSeconds(2),
                        null,
                        "artifact_store_delete_failed"
                )
        );

        assertEquals("Failed receipt has inconsistent failure evidence.", exception.getMessage());
    }

    @Test
    void transitionRejectsAChangedImmutableIdentity() throws Exception {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(REQUEST_ID, TENANT_ID, JOB_ID, CHECKSUM, AUDIT_ID, REQUESTED_AT);
        Method transition = ArtifactDeletionLedger.class.getDeclaredMethod(
                "transition",
                UUID.class,
                UnaryOperator.class
        );
        transition.setAccessible(true);
        UnaryOperator<ArtifactDeletionReceipt> changeTenant = current -> new ArtifactDeletionReceipt(
                current.requestId(),
                "tenant-south",
                current.jobId(),
                current.artifactChecksum(),
                current.auditCorrelationId(),
                current.requestedAt(),
                current.stateChangedAt(),
                current.state(),
                current.attemptCount(),
                current.lastAttemptAt(),
                current.completedAt(),
                current.failureCode()
        );

        InvocationTargetException invocation = assertThrows(
                InvocationTargetException.class,
                () -> transition.invoke(ledger, JOB_ID, changeTenant)
        );

        assertEquals(
                "Artifact deletion receipt transition changed immutable identity.",
                invocation.getCause().getMessage()
        );
        assertEquals(TENANT_ID, ledger.findByJobId(JOB_ID).orElseThrow().tenantId());
    }

    private static void assertInvalidLedger(Path path) {
        assertEquals(
                "artifact deletion ledger contains an invalid line",
                assertThrows(IllegalStateException.class, () -> new ArtifactDeletionLedger(path)).getMessage()
        );
    }
}
