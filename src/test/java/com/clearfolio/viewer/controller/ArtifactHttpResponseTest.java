package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ArtifactHttpResponseTest {

    @Test
    void unauthorizedTokenFailureAdvertisesBearerChallenge() {
        var response = ArtifactHttpResponse.tokenFailure(HttpStatus.UNAUTHORIZED);

        assertEquals("Bearer", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void forbiddenTokenFailureDoesNotRestartAuthentication() {
        var response = ArtifactHttpResponse.tokenFailure(HttpStatus.FORBIDDEN);

        assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }
}
