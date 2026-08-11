package com.clearfolio.viewer.api;

import java.util.Set;

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

    private static final Set<String> SUPPORTED_PURPOSES = Set.of(
            "viewer-preview",
            "download",
            "integration"
    );

    /**
     * Validates an explicit purpose against the documented API values.
     */
    public ArtifactLinkRequest {
        if (purpose != null && !purpose.isBlank()
                && !SUPPORTED_PURPOSES.contains(purpose.strip())) {
            throw new IllegalArgumentException("artifact link purpose is unsupported");
        }
    }

    /**
     * Creates the default viewer-preview request.
     *
     * @return default artifact link request
     */
    public static ArtifactLinkRequest viewerPreview() {
        return new ArtifactLinkRequest("viewer-preview", null, null);
    }
}
