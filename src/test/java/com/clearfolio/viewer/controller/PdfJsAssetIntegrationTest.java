package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that the configured PDF.js module and worker are packaged together
 * and that Clearfolio preserves its signed-artifact integration path.
 */
class PdfJsAssetIntegrationTest {

    private static final String PDF_JS_VERSION = "6.1.200";
    private static final String WEBJAR_ROOT = "/META-INF/resources/webjars/pdfjs-dist/" + PDF_JS_VERSION;

    @Test
    void packagesMatchingDisplayModuleAndWorkerAssets() throws Exception {
        assertPackagedResource(WEBJAR_ROOT + "/build/pdf.mjs");
        assertPackagedResource(WEBJAR_ROOT + "/build/pdf.worker.mjs");
    }

    @Test
    void viewerShellPublishesTheSameVersionedModuleAndWorkerPaths() {
        WebTestClient.bindToController(new ViewerUiController())
                .build()
                .get()
                .uri("/viewer/{docId}", UUID.randomUUID())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains(
                            "clearfolio-pdfjs-module-path\" content=\""
                                    + ViewerUiController.PDF_JS_MODULE_PATH
                                    + "\""
                    ));
                    assertTrue(body.contains(
                            "clearfolio-pdfjs-worker-path\" content=\""
                                    + ViewerUiController.PDF_JS_WORKER_PATH
                                    + "\""
                    ));
                });
    }

    @Test
    void viewerScriptKeepsTheVersionAndSignedArtifactTokenFlowAligned() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/viewer.js")) {
            assertNotNull(input, "viewer.js must be packaged");
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains(ViewerUiController.PDF_JS_MODULE_PATH));
            assertTrue(script.contains(ViewerUiController.PDF_JS_WORKER_PATH));
            assertTrue(script.contains("GlobalWorkerOptions.workerSrc"));
            assertTrue(script.contains("getDocument({"));
            assertTrue(script.contains("get(\"artifactToken\")"));
            assertTrue(script.contains("artifactToken=${encodeURIComponent(externalArtifactToken)}"));
            assertTrue(script.contains("await renderPdfInline(artifactPath)"));
        }
    }

    private static void assertPackagedResource(String resourcePath) throws Exception {
        try (InputStream input = PdfJsAssetIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(input, () -> "missing PDF.js asset: " + resourcePath);
            assertTrue(input.read() >= 0, () -> "empty PDF.js asset: " + resourcePath);
        }
    }
}
