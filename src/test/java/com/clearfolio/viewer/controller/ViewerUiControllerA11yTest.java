package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.service.DocumentConversionService;

class ViewerUiControllerA11yTest {

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        ViewerUiController controller = new ViewerUiController(conversionService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void viewerHtmlIncludesA11yAttributesForExternalLinks() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("role=\"group\""), "Should contain role=\"group\" for actions container");
                    assertTrue(body.contains("target=\"_blank\""), "Should contain target=\"_blank\" for external links");
                    assertTrue(body.contains("aria-label=\"Service status (opens in a new tab)\""), "Should contain aria-label for service status link");
                    assertTrue(body.contains("aria-label=\"Open JSON bootstrap (opens in a new tab)\""), "Should contain aria-label for JSON bootstrap link");
                    assertTrue(body.contains("external-link-indicator"), "Should use CSP-compatible indicator styling");
                    assertTrue(body.contains("&nearr;"), "Should contain visual indicator for external links");
                });
    }
}
