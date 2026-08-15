package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactLinkLedgerDurabilityTest {

    @Test
    void doesNotPublishIssuedAuthorityWhenDurableAcknowledgementFails(@TempDir Path tempDir) {
        Path ledgerPath = tempDir.resolve("artifact-links.ledger");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath, (path, line) -> {
            Files.writeString(
                    path,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
            throw new IOException("durable acknowledgement failed");
        });
        ArtifactLinkRecord record = issuedRecord();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ledger.recordIssued(record)
        );

        assertEquals("artifact link ledger cannot be written", failure.getMessage());
        assertTrue(ledger.findByTokenId(record.tokenId()).isEmpty());
        assertTrue(Files.exists(ledgerPath));
    }

    private static ArtifactLinkRecord issuedRecord() {
        Instant issuedAt = Instant.parse("2026-08-11T04:00:00Z");
        return new ArtifactLinkRecord(
                "token-1",
                "tenant-1",
                "subject-1",
                UUID.fromString("00000000-0000-0000-0000-000000000357"),
                "artifact:read",
                "viewer-preview",
                "checksum",
                null,
                issuedAt,
                issuedAt.plusSeconds(300),
                null,
                null,
                null
        );
    }
}
