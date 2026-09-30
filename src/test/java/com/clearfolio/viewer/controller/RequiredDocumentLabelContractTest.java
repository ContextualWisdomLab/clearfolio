package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RequiredDocumentLabelContractTest {

    @Test
    void homeMarksDocumentInputAsRequiredInVisibleCopy() {
        WebTestClient.bindToController(new ViewerUiController())
                .build()
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("Document (required)")));
    }
}
