package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

class ViewerUiControllerTest {

    private DocumentConversionService conversionService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        ViewerUiController controller = new ViewerUiController(conversionService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void homeReturnsBuyerDemoUploadShell() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("Document intake"));
                    assertTrue(body.contains("id=\"upload-form\""));
                    assertTrue(body.contains("name=\"file\""));
                    assertTrue(body.contains("id=\"session-history\""));
                    assertTrue(body.contains("id=\"kpi-strip\""));
                    assertTrue(body.contains("/assets/viewer/demo.js"));
                    assertTrue(body.contains("/assets/viewer/viewer.css"));
                });
    }

    @Test
    void viewerReturnsNotFoundHtmlWhenJobMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId));
                    assertTrue(body.contains("clearfolio-initial-state\" content=\"NOT_FOUND\""));
                    assertTrue(body.contains("/assets/viewer/viewer.css"));
                    assertTrue(body.contains("/assets/viewer/viewer.js"));
                    assertTrue(body.contains("target=\"_blank\" rel=\"noopener noreferrer\""));
                    assertTrue(body.contains("aria-label=\"Open JSON bootstrap in a new tab\""));
                });
    }

    @Test
    void viewerScriptOpensArtifactLinksInNewContextAndClearsHelpText() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/viewer.js")) {
            assertNotNull(input);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(Pattern.compile("link\\.target\\s*=\\s*\"_blank\"").matcher(script).find());
            assertTrue(Pattern.compile("link\\.rel\\s*=\\s*\"noopener noreferrer\"").matcher(script).find());
            assertTrue(Pattern.compile("aria-label\"\\s*,\\s*\"Open artifact in a new tab\"").matcher(script).find());
            assertTrue(Pattern.compile("querySelector\\(\\s*\"#preview-help\"\\s*\\)").matcher(script).find());
        }
    }

    @Test
    void demoScriptUsesExistingApiAndSessionHistory() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/demo.js")) {
            assertNotNull(input);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains("/api/v1/convert/jobs"));
            assertTrue(script.contains("/viewer/"));
            assertTrue(script.contains("FormData"));
            assertTrue(script.contains("localStorage"));
            assertTrue(script.contains("clearfolio-demo-history-v1"));
            assertTrue(script.contains("setTimeout"));
        }
    }

    @Test
    void viewerReturnsLoadingHtmlWhenSubmitted() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-initial-state\" content=\"LOADING\"")));
    }

    @Test
    void viewerReturnsFailedHtmlWhenJobFailed() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markFailed("boom");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-initial-state\" content=\"FAILED\"")));
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededWithConvertedResourcePath() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("/artifacts/" + docId + ".pdf", "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId)));
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededButConvertedResourcePathIsBlank() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("   ", "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededButConvertedResourcePathIsNull() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded(null, "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
