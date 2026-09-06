package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ArtifactLinkStructuralAdmissionTest {

    @Test
    void malformedFieldCardinalityFailsBeforeHmac() throws Exception {
        ArtifactLinkService service = new ArtifactLinkService(
                new InMemoryArtifactStore(),
                "test-secret"
        );
        Field signingKey = ArtifactLinkService.class.getDeclaredField("signingKey");
        signingKey.setAccessible(true);
        signingKey.set(service, null);

        ArtifactTokenException exception = assertThrows(
                ArtifactTokenException.class,
                () -> service.verifyReadToken(
                        UUID.randomUUID(),
                        null,
                        new byte[0],
                        "payload.signature"
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
