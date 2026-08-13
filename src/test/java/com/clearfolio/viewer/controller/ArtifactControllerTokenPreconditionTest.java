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
 * Proves that the public artifact route authenticates supplied credentials before
 * reading conversion-job or artifact state that could become an existence oracle.
 */
class ArtifactControllerTokenPreconditionTest {

    @Test
    void missingArtifactTokenIsRejectedBeforeSensitiveObjectLookup() {
        Fixture fixture = fixture();

        ResponseEntity<byte[]> response = fixture.controller().getPdf(
                UUID.randomUUID(), null, null, null, null).block();

        assertUnauthorizedWithoutObjectLookup(response, fixture);
    }

    @Test
    void unsupportedAuthorizationSchemeIsRejectedBeforeSensitiveObjectLookup() {
        Fixture fixture = fixture();

        ResponseEntity<byte[]> response = fixture.controller().getPdf(
                UUID.randomUUID(), null, "   ", "Basic opaque-credential", null).block();

        assertUnauthorizedWithoutObjectLookup(response, fixture);
    }

    @Test
    void invalidQueryTokenIsRejectedBeforeSensitiveObjectLookup() {
        Fixture fixture = fixture();

        ResponseEntity<byte[]> response = fixture.controller().getPdf(
                UUID.randomUUID(), null, "opaque-present-token", null, null).block();

        assertUnauthorizedWithoutObjectLookup(response, fixture);
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeSensitiveObjectLookup() {
        Fixture fixture = fixture();

        ResponseEntity<byte[]> response = fixture.controller().getPdf(
                UUID.randomUUID(), null, null, "Bearer opaque-present-token", null).block();

        assertUnauthorizedWithoutObjectLookup(response, fixture);
    }

    private static Fixture fixture() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ArtifactLinkService artifactLinkService = new ArtifactLinkService(artifactStore, "test-secret");
        ArtifactController controller = new ArtifactController(
                conversionService,
                artifactStore,
                artifactLinkService,
                mock(TenantAccessService.class)
        );
        return new Fixture(controller, conversionService, artifactStore);
    }

    private static void assertUnauthorizedWithoutObjectLookup(
            ResponseEntity<byte[]> response,
            Fixture fixture) {
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(fixture.conversionService(), fixture.artifactStore());
    }

    private record Fixture(
            ArtifactController controller,
            DocumentConversionService conversionService,
            ArtifactStore artifactStore) {
    }
}
