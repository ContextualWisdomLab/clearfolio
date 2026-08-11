package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
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
}
