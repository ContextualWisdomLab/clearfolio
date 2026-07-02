package com.clearfolio.viewer.api;

/**
 * Request payload for issuing a short-lived artifact access link.
 *
 * @param purpose caller-visible reason for the artifact link
 * @param ttlSeconds requested token time to live in seconds
 * @param viewerSessionId optional browser viewer session identifier
 */
public record ArtifactLinkRequest(
        String purpose,
        Integer ttlSeconds,
        String viewerSessionId
) {

    /**
     * Creates the default viewer-preview request.
     *
     * @return default artifact link request
     */
    public static ArtifactLinkRequest viewerPreview() {
        return new ArtifactLinkRequest("viewer-preview", null, null);
    }
}
