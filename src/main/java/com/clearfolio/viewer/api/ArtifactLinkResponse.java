package com.clearfolio.viewer.api;

import java.time.Instant;

/**
 * Response payload for a tenant-bound artifact access link.
 *
 * @param artifactUrl signed URL that can be loaded by PDF.js
 * @param expiresAt token expiry timestamp
 * @param tokenId token identifier for audit and future revocation
 * @param scope token scope
 * @param docId document identifier bound to the token
 */
public record ArtifactLinkResponse(
        String artifactUrl,
        Instant expiresAt,
        String tokenId,
        String scope,
        String docId
) {
}
