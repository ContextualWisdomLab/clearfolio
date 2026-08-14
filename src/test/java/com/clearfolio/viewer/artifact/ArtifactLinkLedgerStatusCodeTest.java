package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactLinkLedgerStatusCodeTest {

    @TempDir
    private Path tempDir;

    @Test
    void rejectsPersistedReadStatusOutsideHttpStatusRange() throws Exception {
        assertInvalidStatus("99");
        assertInvalidStatus("600");
    }

    @Test
    void acceptsPersistedReadStatusAtHttpStatusBoundaries() throws Exception {
        assertValidStatus("100");
        assertValidStatus("599");
    }

    private void assertInvalidStatus(String statusCode) throws Exception {
        Path ledgerPath = writeReadLine(statusCode);

        assertThrows(IllegalStateException.class, () -> new ArtifactLinkLedger(ledgerPath));
    }

    private void assertValidStatus(String statusCode) throws Exception {
        Path ledgerPath = writeReadLine(statusCode);

        assertDoesNotThrow(() -> new ArtifactLinkLedger(ledgerPath));
    }

    private Path writeReadLine(String statusCode) throws Exception {
        Path ledgerPath = tempDir.resolve(UUID.randomUUID() + ".log");
        UUID docId = UUID.randomUUID();
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(new ArtifactLinkRecord(
                "token-1",
                "tenant-a",
                "subject-a",
                docId,
                ArtifactLinkService.ARTIFACT_READ_SCOPE,
                "viewer-preview",
                "checksum",
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                null,
                null,
                null
        ));
        String line = String.join("\t",
                "READ",
                encoded("tenant-a"),
                encoded("subject-a"),
                docId.toString(),
                encoded("token-1"),
                "-",
                statusCode,
                encoded("trace-1"),
                Instant.EPOCH.toString()
        );
        Files.writeString(
                ledgerPath,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
        return ledgerPath;
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
