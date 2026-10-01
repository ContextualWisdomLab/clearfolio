package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ViewerUiRequiredFieldAccessibilityTest {

    @Test
    void requiredFieldsUseExplicitTextIndicatorAndSpanWrapper() {
        WebTestClient webTestClient = WebTestClient.bindToController(new ViewerUiController()).build();

        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("<label class=\"field-label\" for=\"file-input\"><span>Document (required)</span></label>"));
                });
    }
}
