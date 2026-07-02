package com.clearfolio.viewer.api;

import java.time.Instant;

/**
 * Response returned after an artifact link revocation request.
 *
 * @param tokenId revoked token identifier
 * @param revokedAt timestamp when the token was revoked
 * @param revokedBy subject that requested revocation
 * @param reason recorded revocation reason
 * @param revoked whether the token is now revoked
 */
public record ArtifactLinkRevocationResponse(
        String tokenId,
        Instant revokedAt,
        String revokedBy,
        String reason,
        boolean revoked
) {
}
