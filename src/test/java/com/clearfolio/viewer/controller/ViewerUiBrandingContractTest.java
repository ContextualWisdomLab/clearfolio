package com.clearfolio.viewer.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Regression tests that keep the distributable Clearfolio UI free of unrelated company branding.
 */
class ViewerUiBrandingContractTest {

    private WebTestClient webTestClient;

    /**
     * Creates a controller-only client for deterministic HTML contract checks.
     */
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ViewerUiController()).build();
    }

    /**
     * Requires the root shell to identify the product without claiming HYOSUNG ownership.
     */
    @Test
    void rootShellDoesNotClaimHyosungOwnership() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Clearfolio Viewer");
                    assertThat(body).doesNotContain("HYOSUNG");
                });
    }

    /**
     * Requires document viewer shells to use the same product-owned branding contract.
     */
    @Test
    void documentViewerDoesNotClaimHyosungOwnership() {
        webTestClient.get()
                .uri("/viewer/{docId}", UUID.randomUUID())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Clearfolio Viewer");
                    assertThat(body).doesNotContain("HYOSUNG");
                });
    }
}
