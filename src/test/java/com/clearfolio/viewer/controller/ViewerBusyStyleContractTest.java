package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ViewerBusyStyleContractTest {

    @Test
    void busyFeedbackCoversPreviewRenderingAndReducedMotion() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/viewer.css")) {
            assertNotNull(input);
            String stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(stylesheet.contains(".btn[aria-busy=\"true\"]::before,\n.preview[aria-busy=\"true\"]::before"));
            assertTrue(stylesheet.contains(".skeleton,\n  .btn[aria-busy=\"true\"]::before,\n  .preview[aria-busy=\"true\"]::before {\n    animation: none;"));
        }
    }
}
