package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that configured durable-ledger write failures do not publish only
 * process-local authority or audit state.
 */
class ArtifactLinkLedgerFailClosedTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void failedIssuePersistenceDoesNotPublishInMemoryToken() throws Exception {
        Path blockedParent = temporaryDirectory.resolve("issue-blocked");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(blockedParent.resolve("ledger.log"));
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);
        ArtifactLinkRecord record = record("token-issue", UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> ledger.recordIssued(record));

        assertTrue(ledger.findByTokenId(record.tokenId()).isEmpty());
    }

    @Test
    void failedRevocationPersistenceDoesNotPublishInMemoryRevocation() throws Exception {
        Path parent = temporaryDirectory.resolve("revoke-parent");
        Path ledgerPath = parent.resolve("ledger.log");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ArtifactLinkRecord record = record("token-revoke", UUID.randomUUID());
        ledger.recordIssued(record);
        blockFutureWrites(parent, ledgerPath);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.revoke(record.tokenId(), Instant.EPOCH.plusSeconds(2), "operator", "test")
        );

        assertFalse(ledger.findByTokenId(record.tokenId()).orElseThrow().isRevoked());
    }

    @Test
    void failedReadPersistenceDoesNotPublishInMemoryAuditEvent() throws Exception {
        Path blockedParent = temporaryDirectory.resolve("read-blocked");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(blockedParent.resolve("ledger.log"));
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);
        UUID docId = UUID.randomUUID();
        ArtifactReadEvent event = new ArtifactReadEvent(
                "tenant-a",
                "subject-a",
                docId,
                "token-read",
                null,
                200,
                "trace-1",
                Instant.EPOCH
        );

        assertThrows(IllegalStateException.class, () -> ledger.recordRead(event));

        assertTrue(ledger.readEventsFor("tenant-a", docId).isEmpty());
    }

    private static ArtifactLinkRecord record(String tokenId, UUID docId) {
        return new ArtifactLinkRecord(
                tokenId,
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
        );
    }

    private static void blockFutureWrites(Path parent, Path ledgerPath) throws Exception {
        Files.delete(ledgerPath);
        Files.delete(parent);
        Files.writeString(parent, "not a directory", StandardCharsets.UTF_8);
    }
}
