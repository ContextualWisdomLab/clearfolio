package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ViewerUiRequiredMarkerCspContractTest {

    @Test
    void requiredMarkerUsesStylesheetClassCompatibleWithStrictCsp() throws Exception {
        WebTestClient client = WebTestClient.bindToController(new ViewerUiController()).build();

        client.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("class=\"required-marker\""));
                    assertTrue(body.contains("aria-hidden=\"true\">*</span>"));
                    assertFalse(body.contains("style=\"color: var(--danger);\""));
                });

        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/viewer.css")) {
            assertNotNull(input);
            String stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(stylesheet.contains(".required-marker"));
            assertTrue(stylesheet.contains("color: var(--danger)"));
        }
    }
}
