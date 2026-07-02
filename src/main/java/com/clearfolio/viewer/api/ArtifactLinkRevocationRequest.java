package com.clearfolio.viewer.api;

/**
 * Request payload for revoking a previously issued artifact link.
 *
 * @param reason operator-visible revocation reason
 */
public record ArtifactLinkRevocationRequest(String reason) {
}
