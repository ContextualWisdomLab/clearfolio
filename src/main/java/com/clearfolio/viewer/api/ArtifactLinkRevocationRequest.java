package com.clearfolio.viewer.api;

/**
 * Request payload for revoking a previously issued artifact link.
 *
 * <p>Explicit operator reasons are bounded before they can enter the durable
 * artifact-link ledger. A missing reason remains valid so the service can use
 * its documented default reason.</p>
 *
 * @param reason operator-visible revocation reason, at most 256 Unicode code points
 */
public record ArtifactLinkRevocationRequest(String reason) {

    private static final int MAX_REASON_CODE_POINTS = 256;

    /**
     * Validates the bounded durable-reason contract.
     *
     * @param reason operator-visible revocation reason
     */
    public ArtifactLinkRevocationRequest {
        if (reason != null && reason.codePointCount(0, reason.length()) > MAX_REASON_CODE_POINTS) {
            throw new IllegalArgumentException("artifact link revocation reason exceeds 256 characters");
        }
    }
}
