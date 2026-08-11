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

class ArtifactLinkLedgerReadAuthorityTest {

    @TempDir
    private Path tempDir;

    @Test
    void rejectsReadEventsWithoutMatchingIssuedIdentity() {
        ArtifactLinkLedger ledger = new ArtifactLinkLedger();
        ArtifactLinkRecord record = issuedRecord("token-1");
        ledger.recordIssued(record);

        assertThrows(IllegalStateException.class, () -> ledger.recordRead(null));
        assertThrows(IllegalStateException.class, () -> ledger.recordRead(readEvent(
                null, record.tenantId(), record.subjectId(), record.docId())));
        assertThrows(IllegalStateException.class, () -> ledger.recordRead(readEvent(
                "unknown-token", record.tenantId(), record.subjectId(), record.docId())));
        assertThrows(IllegalStateException.class, () -> ledger.recordRead(readEvent(
                record.tokenId(), "tenant-b", record.subjectId(), record.docId())));
        assertThrows(IllegalStateException.class, () -> ledger.recordRead(readEvent(
                record.tokenId(), record.tenantId(), "subject-b", record.docId())));
        assertThrows(IllegalStateException.class, () -> ledger.recordRead(readEvent(
                record.tokenId(), record.tenantId(), record.subjectId(), UUID.randomUUID())));
    }

    @Test
    void rejectsTamperedReadAuthorityDuringReplay() throws Exception {
        ArtifactLinkRecord record = issuedRecord("token-replay");

        assertReplayRejected(record, readLine(
                "unknown-token", record.tenantId(), record.subjectId(), record.docId()));
        assertReplayRejected(record, readLine(
                record.tokenId(), "tenant-b", record.subjectId(), record.docId()));
        assertReplayRejected(record, readLine(
                record.tokenId(), record.tenantId(), "subject-b", record.docId()));
        assertReplayRejected(record, readLine(
                record.tokenId(), record.tenantId(), record.subjectId(), UUID.randomUUID()));
    }

    @Test
    void acceptsReadBoundToIssuedIdentity() throws Exception {
        Path ledgerPath = tempDir.resolve("matching-read.log");
        ArtifactLinkRecord record = issuedRecord("token-valid");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(record);
        ledger.recordRead(readEvent(record.tokenId(), record.tenantId(), record.subjectId(), record.docId()));

        ArtifactLinkLedger reloaded = new ArtifactLinkLedger(ledgerPath);

        assertEquals(1, reloaded.readEventsFor(record.tenantId(), record.docId()).size());
    }

    private void assertReplayRejected(ArtifactLinkRecord record, String readLine) throws Exception {
        Path ledgerPath = tempDir.resolve(UUID.randomUUID() + ".log");
        ArtifactLinkLedger ledger = new ArtifactLinkLedger(ledgerPath);
        ledger.recordIssued(record);
        appendLine(ledgerPath, readLine);

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

    private static ArtifactReadEvent readEvent(String tokenId, String tenantId, String subjectId, UUID docId) {
        return new ArtifactReadEvent(
                tenantId,
                subjectId,
                docId,
                tokenId,
                null,
                200,
                "trace-1",
                Instant.EPOCH.plusSeconds(1)
        );
    }

    private static String readLine(String tokenId, String tenantId, String subjectId, UUID docId) {
        return String.join("\t",
                "READ",
                encoded(tenantId),
                encoded(subjectId),
                docId.toString(),
                encoded(tokenId),
                "-",
                "200",
                encoded("trace-1"),
                Instant.EPOCH.plusSeconds(1).toString()
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
