package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that restart replay never promotes an uncommitted ledger tail.
 */
class ArtifactDeletionLedgerCrashTailTest {

    private static final UUID REQUEST_ID = UUID.fromString("12121212-3434-5656-7878-909090909090");
    private static final UUID JOB_ID = UUID.fromString("abababab-cdcd-efef-1212-343434343434");
    private static final String CHECKSUM = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-06T09:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void unterminatedFinalRecordFailsClosedAfterRestart() throws IOException {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        ArtifactDeletionLedger firstProcess = new ArtifactDeletionLedger(ledgerPath);
        firstProcess.request(
                REQUEST_ID,
                "tenant-crash-recovery",
                JOB_ID,
                CHECKSUM,
                "audit-v1:crash-tail-regression",
                REQUESTED_AT
        );

        byte[] durableBytes = Files.readAllBytes(ledgerPath);
        byte[] lineTerminator = {'\n'};
        assertTrue(endsWith(durableBytes, lineTerminator), "ledger append did not emit its commit delimiter");
        assertTrue(
                durableBytes.length < 2 || durableBytes[durableBytes.length - 2] != '\r',
                "ledger format must use host-independent LF rather than CRLF"
        );
        Files.write(
                ledgerPath,
                Arrays.copyOf(durableBytes, durableBytes.length - lineTerminator.length)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ArtifactDeletionLedger(ledgerPath)
        );

        assertEquals("artifact deletion ledger contains an invalid line", exception.getMessage());
    }

    private static boolean endsWith(byte[] value, byte[] suffix) {
        if (value.length < suffix.length) {
            return false;
        }
        int offset = value.length - suffix.length;
        for (int index = 0; index < suffix.length; index++) {
            if (value[offset + index] != suffix[index]) {
                return false;
            }
        }
        return true;
    }
}
