package com.clearfolio.viewer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerUiRequiredFieldAccessibilityTest {

    @Test
    void explicitAccessiblePatternForRequiredFormFields() {
        ViewerUiController controller = new ViewerUiController();
        WebTestClient webTestClient = WebTestClient.bindToController(controller).build();

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
