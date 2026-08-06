package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.UUID;

/**
 * Verified artifact token claims.
 *
 * @param tokenId artifact token identifier
 * @param tenantId tenant that owns the artifact
 * @param subjectId subject encoded in the token
 * @param docId document identifier bound to the token
 * @param scope token scope
 * @param purpose caller-visible token purpose
 * @param artifactChecksum artifact checksum bound to the token
 * @param issuedAt token issue timestamp
 * @param expiresAt token expiration timestamp
 */
public record ArtifactTokenClaims(
        String tokenId,
        String tenantId,
        String subjectId,
        UUID docId,
        String scope,
        String purpose,
        String artifactChecksum,
        Instant issuedAt,
        Instant expiresAt
) {
}
