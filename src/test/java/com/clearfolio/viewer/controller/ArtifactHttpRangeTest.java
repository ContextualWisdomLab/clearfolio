package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Boundary regressions for Clearfolio's shared single-range parser.
 */
class ArtifactHttpRangeTest {

    @Test
    void rejectsSuffixRangeForEmptyArtifact() {
        var range = ArtifactHttpRange.resolveSingleRange("bytes=-1", 0);

        assertTrue(range.isPresent());
        assertTrue(range.get().rejected());
    }

    @Test
    void rejectsExplicitPlusSignInFirstPosition() {
        var range = ArtifactHttpRange.resolveSingleRange("bytes=+1-2", 10);

        assertTrue(range.isPresent());
        assertTrue(range.get().rejected());
    }

    @Test
    void rejectsExplicitPlusSignInSuffixLength() {
        var range = ArtifactHttpRange.resolveSingleRange("bytes=-+1", 10);

        assertTrue(range.isPresent());
        assertTrue(range.get().rejected());
    }
}
