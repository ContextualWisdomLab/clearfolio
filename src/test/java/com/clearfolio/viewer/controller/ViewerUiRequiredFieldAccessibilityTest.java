package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ViewerUiRequiredFieldAccessibilityTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        ViewerUiController controller = new ViewerUiController();
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void viewerRendersAccessibleRequiredField() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("for=\"file-input\""));
                    assertTrue(body.contains("<span>Document (required)</span>"));
                });
    }
}
