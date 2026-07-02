package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

/**
 * Runtime ledger for issued artifact links and artifact read audit events.
 */
@Repository
public class ArtifactLinkLedger {

    private final ConcurrentMap<String, ArtifactLinkRecord> issuedLinks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ArtifactReadEvent> readEvents = new ConcurrentLinkedQueue<>();

    /**
     * Records an issued artifact link.
     *
     * @param record issued artifact link record
     */
    public void recordIssued(ArtifactLinkRecord record) {
        issuedLinks.put(record.tokenId(), record);
    }

    /**
     * Finds an issued artifact link by token identifier.
     *
     * @param tokenId token identifier
     * @return matching record when present
     */
    public Optional<ArtifactLinkRecord> findByTokenId(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(issuedLinks.get(tokenId));
    }

    /**
     * Marks an issued artifact link as revoked.
     *
     * @param tokenId token identifier
     * @param revokedAt revocation timestamp
     * @param revokedBy subject requesting revocation
     * @param reason recorded reason
     * @return updated record when the token exists
     */
    public Optional<ArtifactLinkRecord> revoke(
            String tokenId,
            Instant revokedAt,
            String revokedBy,
            String reason) {
        return Optional.ofNullable(issuedLinks.computeIfPresent(tokenId, (ignored, current) -> {
            if (current.isRevoked()) {
                return current;
            }
            return current.revoked(revokedAt, revokedBy, reason);
        }));
    }

    /**
     * Records a verified artifact read.
     *
     * @param event artifact read event
     */
    public void recordRead(ArtifactReadEvent event) {
        readEvents.add(event);
    }

    /**
     * Returns read events for a tenant-owned document.
     *
     * @param tenantId tenant identifier
     * @param docId document identifier
     * @return current matching read events
     */
    public List<ArtifactReadEvent> readEventsFor(String tenantId, UUID docId) {
        return readEvents.stream()
                .filter(event -> event.tenantId().equals(tenantId))
                .filter(event -> event.docId().equals(docId))
                .toList();
    }
}
