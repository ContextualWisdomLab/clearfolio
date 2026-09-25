package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ViewerUiRequiredFieldAccessibilityTest {

    @Test
    void uploadFileFieldProvidesVisibleRequiredTextAndNativeRequiredState() {
        WebTestClient.bindToController(new ViewerUiController())
                .build()
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("<label class=\"field-label\" for=\"file-input\">Document (required)</label>"));
                    assertTrue(body.contains("<input id=\"file-input\" name=\"file\" class=\"file-input\" type=\"file\" required />"));
                    assertTrue(!body.contains("<span class=\"error__title\" aria-hidden=\"true\">*</span>"));
                });
    }
}
