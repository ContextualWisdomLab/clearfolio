package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Regression tests for security-sensitive artifact token failure response headers.
 */
class ArtifactHttpResponseTest {

    /**
     * Requires a bearer challenge while preserving cache and MIME-sniffing defenses on 401.
     */
    @Test
    void unauthorizedTokenFailureAdvertisesBearerChallenge() {
        var response = ArtifactHttpResponse.tokenFailure(HttpStatus.UNAUTHORIZED);

        assertEquals("Bearer", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    /**
     * Keeps 403 from restarting authentication while preserving the standard security headers.
     */
    @Test
    void forbiddenTokenFailureDoesNotRestartAuthentication() {
        var response = ArtifactHttpResponse.tokenFailure(HttpStatus.FORBIDDEN);

        assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }
}
