package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * Proves that the public artifact route authenticates token presence before
 * reading conversion-job or artifact state that could become an existence oracle.
 */
class ArtifactControllerTokenPreconditionTest {

    @Test
    void missingArtifactTokenIsRejectedBeforeSensitiveObjectLookup() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ArtifactLinkService artifactLinkService = mock(ArtifactLinkService.class);
        ArtifactController controller = new ArtifactController(
                conversionService,
                artifactStore,
                artifactLinkService,
                mock(TenantAccessService.class)
        );

        ResponseEntity<byte[]> response = controller.getPdf(
                UUID.randomUUID(),
                null,
                null,
                null,
                null
        ).block();

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(conversionService, artifactStore, artifactLinkService);
    }

    @Test
    void unsupportedAuthorizationSchemeIsRejectedBeforeSensitiveObjectLookup() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ArtifactLinkService artifactLinkService = mock(ArtifactLinkService.class);
        ArtifactController controller = new ArtifactController(
                conversionService,
                artifactStore,
                artifactLinkService,
                mock(TenantAccessService.class)
        );

        ResponseEntity<byte[]> response = controller.getPdf(
                UUID.randomUUID(),
                null,
                "   ",
                "Basic opaque-credential",
                null
        ).block();

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(conversionService, artifactStore, artifactLinkService);
    }
}
