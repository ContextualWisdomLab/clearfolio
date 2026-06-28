package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ViewerUiControllerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        ViewerUiController controller = new ViewerUiController();
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void viewerReturnsNeutralShellWithoutProbingJobState() {
        UUID docId = UUID.randomUUID();

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId));
                    assertTrue(body.contains("clearfolio-initial-state\" content=\"LOADING\""));
                    assertTrue(body.contains("/assets/viewer/viewer.css"));
                    assertTrue(body.contains("/assets/viewer/viewer.js"));
                    String openJsonLink = openJsonLinkTag(body);
                    assertTrue(openJsonLink.contains("id=\"open-json-link\""));
                    assertTrue(openJsonLink.contains("target=\"_blank\""));
                    assertTrue(openJsonLink.contains("rel=\"noopener noreferrer\""));
                    assertTrue(openJsonLink.contains("aria-label=\"Open JSON bootstrap (opens in new tab)\""));
                });
    }

    private static String openJsonLinkTag(String body) {
        int idIndex = body.indexOf("id=\"open-json-link\"");
        assertTrue(idIndex >= 0, "open-json-link anchor should be present");
        int start = body.lastIndexOf("<a", idIndex);
        int end = body.indexOf('>', idIndex);
        assertTrue(start >= 0 && end > start, "open-json-link anchor start tag should be present");
        return body.substring(start, end + 1);
    }
}
