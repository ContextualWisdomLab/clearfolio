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

    private static final String VIEWER_PREVIEW_PURPOSE = "viewer-preview";
    private static final String LEGACY_VIEWER_PURPOSE = "viewer";
    private static final Set<String> SUPPORTED_PURPOSES = Set.of(
            VIEWER_PREVIEW_PURPOSE,
            "download",
            "integration"
    );

    /**
     * Canonicalizes legacy/default input and validates the purpose against the
     * documented values that may be signed into an artifact token.
     *
     * @param purpose caller-visible reason for the artifact link
     * @param ttlSeconds requested token time to live in seconds
     * @param viewerSessionId optional browser viewer session identifier
     */
    public ArtifactLinkRequest {
        purpose = cleanPurpose(purpose);
        if (LEGACY_VIEWER_PURPOSE.equals(purpose)) {
            purpose = VIEWER_PREVIEW_PURPOSE;
        }
        if (purpose != null && !SUPPORTED_PURPOSES.contains(purpose)) {
            throw new IllegalArgumentException("artifact link purpose is unsupported");
        }
    }

    /**
     * Creates the default viewer-preview request.
     *
     * @return default artifact link request
     */
    public static ArtifactLinkRequest viewerPreview() {
        return new ArtifactLinkRequest(VIEWER_PREVIEW_PURPOSE, null, null);
    }

    private static String cleanPurpose(final String value) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("artifact link purpose contains NUL");
        }
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
