package com.clearfolio.viewer.artifact;

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

class ArtifactLinkLedgerRevocationReplayTest {

    @TempDir
    private Path tempDir;

    @Test
    void rejectsRevocationWithoutTimestampDuringReplay() throws Exception {
        Path ledgerPath = tempDir.resolve("missing-timestamp.log");
        ArtifactLinkRecord record = issuedRecord("token-missing-time");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(record);
        appendLine(ledgerPath, revocationLine(record.tokenId(), null, "operator-a", "security-response"));

        assertThrows(IllegalStateException.class, () -> new ArtifactLinkLedger(ledgerPath));
    }

    @Test
    void rejectsDuplicateRevocationTransitionDuringReplay() throws Exception {
        Path ledgerPath = tempDir.resolve("duplicate-revocation.log");
        ArtifactLinkRecord record = issuedRecord("token-duplicate-revoke");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(record);
        ledger.revoke(record.tokenId(), Instant.EPOCH.plusSeconds(1), "operator-a", "first");
        appendLine(
                ledgerPath,
                revocationLine(
                        record.tokenId(),
                        Instant.EPOCH.plusSeconds(2),
                        "operator-b",
                        "replacement"
                )
        );

        assertThrows(IllegalStateException.class, () -> new ArtifactLinkLedger(ledgerPath));
    }

    private static ArtifactLinkRecord issuedRecord(String tokenId) {
        return new ArtifactLinkRecord(
                tokenId,
                "tenant-a",
                "subject-a",
                UUID.randomUUID(),
                ArtifactLinkService.ARTIFACT_READ_SCOPE,
                "viewer-preview",
                "checksum",
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                null,
                null,
                null
        );
    }

    private static String revocationLine(
            String tokenId,
            Instant revokedAt,
            String revokedBy,
            String reason) {
        return String.join("\t",
                "REVOKED",
                encoded(tokenId),
                revokedAt == null ? "-" : revokedAt.toString(),
                encoded(revokedBy),
                encoded(reason)
        );
    }

    private static void appendLine(Path ledgerPath, String line) throws Exception {
        Files.writeString(
                ledgerPath,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
