package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactLinkLedgerTest {

    @TempDir
    private Path tempDir;

    @Test
    void findByTokenIdReturnsEmptyForNullBlankOrMissingToken() {
        ArtifactLinkLedger ledger = new ArtifactLinkLedger();

        assertTrue(ledger.findByTokenId(null).isEmpty());
        assertTrue(ledger.findByTokenId("   ").isEmpty());
        assertTrue(ledger.findByTokenId("missing").isEmpty());
        assertTrue(ledger.revoke("missing", Instant.EPOCH, "operator", "reason").isEmpty());
    }

    @Test
    void recordsIssuedLinksAndReads() {
        ArtifactLinkLedger ledger = new ArtifactLinkLedger();
        UUID docId = UUID.randomUUID();
        ArtifactLinkRecord record = new ArtifactLinkRecord(
                "token-1",
                "tenant-a",
                "subject-a",
                docId,
                ArtifactLinkService.ARTIFACT_READ_SCOPE,
                "viewer-preview",
                "checksum",
                "viewer-session",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                null,
                null,
                null
        );
        ArtifactReadEvent event = new ArtifactReadEvent(
                "tenant-a",
                "subject-a",
                docId,
                "token-1",
                null,
                200,
                null,
                Instant.EPOCH.plusSeconds(1)
        );

        ledger.recordIssued(record);
        ledger.recordRead(event);

        assertEquals(record, ledger.findByTokenId("token-1").orElseThrow());
        assertFalse(record.isRevoked());
        assertEquals(1, ledger.readEventsFor("tenant-a", docId).size());
        assertTrue(ledger.readEventsFor("tenant-b", docId).isEmpty());
        assertTrue(ledger.readEventsFor("tenant-a", UUID.randomUUID()).isEmpty());
    }

    @Test
    void persistsIssuedRevokedAndReadEventsWhenPathIsConfigured() {
        Path ledgerPath = tempDir.resolve("artifact-ledger.log");
        UUID docId = UUID.randomUUID();
        ArtifactLinkRecord record = new ArtifactLinkRecord(
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
        );
        ArtifactReadEvent event = new ArtifactReadEvent(
                "tenant-a",
                "subject-a",
                docId,
                "token-1",
                null,
                206,
                "trace-1",
                Instant.EPOCH.plusSeconds(2)
        );
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);

        ledger.recordIssued(record);
        ledger.revoke("token-1", Instant.EPOCH.plusSeconds(1), "operator-1", "viewer closed");
        ledger.recordRead(event);

        ArtifactLinkLedger reloaded = new ArtifactLinkLedger(ledgerPath);
        ArtifactLinkRecord reloadedRecord = reloaded.findByTokenId("token-1").orElseThrow();
        assertTrue(reloadedRecord.isRevoked());
        assertEquals("operator-1", reloadedRecord.revokedBy());
        assertEquals("viewer closed", reloadedRecord.revokeReason());
        assertEquals(1, reloaded.readEventsFor("tenant-a", docId).size());
        assertEquals(206, reloaded.readEventsFor("tenant-a", docId).getFirst().statusCode());
    }

    @Test
    void stringConstructorTreatsBlankPathAsInMemoryAndMissingPathAsEmpty() {
        ArtifactLinkLedger nullPath = new ArtifactLinkLedger((String) null);
        ArtifactLinkLedger blankPath = new ArtifactLinkLedger(" ");
        ArtifactLinkLedger configuredStringPath = new ArtifactLinkLedger(
                tempDir.resolve("configured-string.log").toString()
        );
        ArtifactLinkLedger missingPath = new ArtifactLinkLedger(tempDir.resolve("missing.log"));

        assertTrue(nullPath.findByTokenId("missing").isEmpty());
        assertTrue(blankPath.findByTokenId("missing").isEmpty());
        assertTrue(configuredStringPath.findByTokenId("missing").isEmpty());
        assertTrue(missingPath.findByTokenId("missing").isEmpty());
    }

    @Test
    void rejectsInvalidPersistedLedgerLines() throws Exception {
        assertInvalidLedger("BROKEN");
        assertInvalidLedger("ISSUED\ttoo-short");
        assertInvalidLedger("REVOKED\ttoo-short");
        assertInvalidLedger("READ\ttoo-short");
        assertInvalidLedger("REVOKED\tdG9rZW4tMQ\t1970-01-01T00:00:00Z\tb3BlcmF0b3I\tcmVhc29u");
        assertInvalidLedger("REVOKED\t-\t1970-01-01T00:00:00Z\tb3BlcmF0b3I\tcmVhc29u");
        assertInvalidLedger(issuedLine("not-a-uuid"));
        assertInvalidLedger(issuedLine(UUID.randomUUID().toString()).replace(encoded("tenant-a"), "!"));
        assertInvalidLedger(issuedLine(UUID.randomUUID().toString()).replace("1970-01-01T00:00:00Z", "not-instant"));
        assertInvalidLedger(readLine("not-status"));
        assertInvalidLedger(readLine("206").replace(encoded("token-1"), encoded(" ")));
    }

    @Test
    void reportsLoadAndWriteFailures() throws Exception {
        Path directory = tempDir.resolve("directory-ledger");
        Files.createDirectory(directory);
        assertThrows(IllegalStateException.class, () -> new ArtifactLinkLedger(directory));

        Path blockedParent = tempDir.resolve("blocked-parent");
        Path ledgerPath = blockedParent.resolve("ledger.log");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);
        ArtifactLinkRecord record = new ArtifactLinkRecord(
                "token-1",
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

        assertThrows(IllegalStateException.class, () -> ledger.recordIssued(record));
    }

    private void assertInvalidLedger(String line) throws Exception {
        Path ledgerPath = Files.writeString(
                tempDir.resolve(UUID.randomUUID() + ".log"),
                line + System.lineSeparator(),
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, () -> new ArtifactLinkLedger(ledgerPath));
    }

    private static String issuedLine(String docId) {
        return String.join("\t",
                "ISSUED",
                encoded("token-1"),
                encoded("tenant-a"),
                encoded("subject-a"),
                docId,
                encoded(ArtifactLinkService.ARTIFACT_READ_SCOPE),
                encoded("viewer-preview"),
                encoded("checksum"),
                "-",
                Instant.EPOCH.toString(),
                Instant.EPOCH.plusSeconds(300).toString(),
                "-",
                "-",
                "-"
        );
    }

    private static String readLine(String statusCode) {
        return String.join("\t",
                "READ",
                encoded("tenant-a"),
                encoded("subject-a"),
                UUID.randomUUID().toString(),
                encoded("token-1"),
                "-",
                statusCode,
                encoded("trace-1"),
                Instant.EPOCH.toString()
        );
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
