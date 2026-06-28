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
                    assertTrue(body.contains("id=\"open-json-link\""));
                    assertTrue(body.contains("target=\"_blank\""));
                    assertTrue(body.contains("rel=\"noopener noreferrer\""));
                });
    }
}
