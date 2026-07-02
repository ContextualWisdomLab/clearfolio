package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime ledger record for an issued artifact link.
 *
 * @param tokenId artifact token identifier
 * @param tenantId tenant that owns the artifact
 * @param subjectId subject that requested the link
 * @param docId document identifier bound to the token
 * @param scope token scope
 * @param purpose caller-visible token purpose
 * @param artifactChecksum artifact checksum bound to the token
 * @param viewerSessionId optional browser viewer session identifier
 * @param issuedAt token issue timestamp
 * @param expiresAt token expiration timestamp
 * @param revokedAt timestamp when the token was revoked
 * @param revokedBy subject that revoked the token
 * @param revokeReason recorded revocation reason
 */
public record ArtifactLinkRecord(
        String tokenId,
        String tenantId,
        String subjectId,
        UUID docId,
        String scope,
        String purpose,
        String artifactChecksum,
        String viewerSessionId,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        String revokeReason
) {

    /**
     * Returns whether the token has been revoked.
     *
     * @return true when revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Creates a copy marked as revoked.
     *
     * @param timestamp revocation timestamp
     * @param subjectId subject requesting revocation
     * @param reason recorded reason
     * @return revoked copy of this record
     */
    public ArtifactLinkRecord revoked(Instant timestamp, String subjectId, String reason) {
        return new ArtifactLinkRecord(
                tokenId,
                tenantId,
                this.subjectId,
                docId,
                scope,
                purpose,
                artifactChecksum,
                viewerSessionId,
                issuedAt,
                expiresAt,
                timestamp,
                subjectId,
                reason
        );
    }
}
