package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.UUID;

record ArtifactTokenClaims(
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
