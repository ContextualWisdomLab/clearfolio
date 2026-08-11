package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ArtifactLinkLedgerAuthorityRebindingTest {

    @TempDir
    private Path tempDir;

    @Test
    void duplicateTokenIdentifierCannotRebindIssuedAuthority() {
        Path ledgerPath = tempDir.resolve("artifact-ledger.log");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ArtifactLinkRecord original = issuedRecord("token-1", "tenant-a", UUID.randomUUID());
        ArtifactLinkRecord conflicting = issuedRecord("token-1", "tenant-b", UUID.randomUUID());

        ledger.recordIssued(original);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ledger.recordIssued(conflicting)
        );

        assertEquals("artifact token identifier is already issued", error.getMessage());
        assertEquals(original, ledger.findByTokenId("token-1").orElseThrow());
        assertEquals(original, new ArtifactLinkLedger(ledgerPath).findByTokenId("token-1").orElseThrow());
    }

    @Test
    void replayRejectsPersistedTokenAuthorityRebinding() throws Exception {
        Path ledgerPath = tempDir.resolve("artifact-ledger-replay.log");
        ArtifactLinkRecord original = issuedRecord("token-1", "tenant-a", UUID.randomUUID());
        ArtifactLinkRecord conflicting = issuedRecord("token-1", "tenant-b", UUID.randomUUID());
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(original);
        Files.writeString(
                ledgerPath,
                issuedLine(conflicting) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ArtifactLinkLedger(ledgerPath)
        );

        assertEquals("artifact link ledger contains an invalid line", error.getMessage());
    }

    private static ArtifactLinkRecord issuedRecord(String tokenId, String tenantId, UUID docId) {
        return new ArtifactLinkRecord(
                tokenId,
                tenantId,
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
        );
    }

    private static String issuedLine(ArtifactLinkRecord record) {
        return String.join("\t",
                "ISSUED",
                encoded(record.tokenId()),
                encoded(record.tenantId()),
                encoded(record.subjectId()),
                record.docId().toString(),
                encoded(record.scope()),
                encoded(record.purpose()),
                encoded(record.artifactChecksum()),
                "-",
                record.issuedAt().toString(),
                record.expiresAt().toString(),
                "-",
                "-",
                "-"
        );
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
