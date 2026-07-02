package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ArtifactLinkLedgerTest {

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
}
