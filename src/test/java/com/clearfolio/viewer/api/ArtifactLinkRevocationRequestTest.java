package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArtifactLinkRevocationRequestTest {

    @Test
    void acceptsBoundedRevocationReason() {
        String reason = "x".repeat(256);

        ArtifactLinkRevocationRequest request = assertDoesNotThrow(
                () -> new ArtifactLinkRevocationRequest(reason)
        );

        assertEquals(reason, request.reason());
    }

    @Test
    void rejectsOversizedRevocationReasonBeforeLedgerPersistence() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactLinkRevocationRequest("x".repeat(257))
        );

        assertEquals("artifact link revocation reason exceeds 256 characters", exception.getMessage());
    }
}
