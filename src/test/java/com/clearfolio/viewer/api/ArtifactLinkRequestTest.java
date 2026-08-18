package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArtifactLinkRequestTest {

    @Test
    void viewerPreviewUsesDocumentedDefaultPurpose() {
        ArtifactLinkRequest request = ArtifactLinkRequest.viewerPreview();

        assertEquals("viewer-preview", request.purpose());
        assertNull(request.ttlSeconds());
        assertNull(request.viewerSessionId());
    }

    @Test
    void acceptsDocumentedArtifactLinkPurposes() {
        assertDoesNotThrow(() -> new ArtifactLinkRequest("viewer-preview", 300, null));
        assertDoesNotThrow(() -> new ArtifactLinkRequest("download", 300, null));
        assertDoesNotThrow(() -> new ArtifactLinkRequest("integration", 300, null));
        assertDoesNotThrow(() -> new ArtifactLinkRequest(null, 300, null));
        assertDoesNotThrow(() -> new ArtifactLinkRequest("   ", 300, null));
    }

    @Test
    void rejectsUndocumentedArtifactLinkPurposeBeforeSigning() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactLinkRequest("admin-export", 300, "viewer-session")
        );

        assertEquals("artifact link purpose is unsupported", exception.getMessage());
    }

    @Test
    void rejectsNulCorruptedDocumentedPurposeBeforeSigning() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactLinkRequest("down\u0000load", 300, "viewer-session")
        );

        assertEquals("artifact link purpose contains NUL", exception.getMessage());
    }
}
