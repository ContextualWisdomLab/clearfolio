package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    void homeReturnsBuyerDemoUploadShell() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("Document intake"));
                    assertTrue(body.contains("id=\"upload-form\""));
                    assertTrue(body.contains("name=\"file\""));
                    assertTrue(body.contains("id=\"session-history\""));
                    assertTrue(body.contains("id=\"job-detail\""));
                    assertTrue(body.contains("id=\"retry-job-btn\""));
                    assertTrue(body.contains("id=\"kpi-strip\""));
                    assertTrue(body.contains("id=\"kpi-total\""));
                    assertTrue(body.contains("id=\"kpi-success-rate\""));
                    assertTrue(body.contains("id=\"kpi-p95\""));
                    assertTrue(body.contains("id=\"kpi-evidence-title\""));
                    assertTrue(body.contains("id=\"kpi-export-count\""));
                    assertTrue(body.contains("id=\"kpi-export-latest\""));
                    assertTrue(body.contains("id=\"kpi-export-subject\""));
                    assertTrue(body.contains("id=\"kpi-export-jobs\""));
                    assertTrue(body.contains("id=\"refresh-evidence-btn\""));
                    assertTrue(body.contains("id=\"operator-recovery-title\""));
                    assertTrue(body.contains("id=\"recovery-needs-action\""));
                    assertTrue(body.contains("id=\"recovery-retry-ready\""));
                    assertTrue(body.contains("id=\"recovery-last-action\""));
                    assertTrue(body.contains("id=\"recovery-latest-inspected\""));
                    assertTrue(body.contains("id=\"load-demo-data-btn\""));
                    assertTrue(body.contains("Load demo story"));
                    assertTrue(body.contains("/assets/viewer/demo.js"));
                    assertTrue(body.contains("/assets/viewer/viewer.css"));
                });
    }

    @Test
    void viewerReturnsLoadingShellWithoutLeakingJobExistence() {
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
                    assertTrue(body.contains("target=\"_blank\" rel=\"noopener noreferrer\""));
                    assertTrue(body.contains("aria-label=\"Open JSON bootstrap in a new tab\""));
                });
    }

    @Test
    void viewerScriptOpensArtifactLinksInNewContextAndClearsHelpText() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/viewer.js")) {
            assertNotNull(input);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(Pattern.compile("link\\.target\\s*=\\s*\"_blank\"").matcher(script).find());
            assertTrue(Pattern.compile("link\\.rel\\s*=\\s*\"noopener noreferrer\"").matcher(script).find());
            assertTrue(Pattern.compile("aria-label\"\\s*,\\s*\"Open artifact in a new tab\"").matcher(script).find());
            assertTrue(Pattern.compile("querySelector\\(\\s*\"#preview-help\"\\s*\\)").matcher(script).find());
        }
    }

    @Test
    void demoScriptUsesExistingApiAndSessionHistory() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/demo.js")) {
            assertNotNull(input);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains("/api/v1/convert/jobs"));
            assertTrue(script.contains("/api/v1/analytics/kpi-snapshot"));
            assertTrue(script.contains("/api/v1/analytics/kpi-snapshot-exports"));
            assertTrue(script.contains("/assets/viewer/demo-fixtures.json"));
            assertTrue(script.contains("/viewer/"));
            assertTrue(script.contains("FormData"));
            assertTrue(script.contains("localStorage"));
            assertTrue(script.contains("clearfolio-demo-history-v1"));
            assertTrue(script.contains("setTimeout"));
            assertTrue(script.contains("formatPercent"));
            assertTrue(script.contains("formatMilliseconds"));
            assertTrue(script.contains("renderKpiEvidence"));
            assertTrue(script.contains("formatTimestamp"));
            assertTrue(script.contains("renderRecoveryEvidence"));
            assertTrue(script.contains("isNeedsAction"));
            assertTrue(script.contains("latestByTimestamp"));
            assertTrue(script.contains("lastRecoveryAction"));
            assertTrue(script.contains("lastInspectedAt"));
            assertTrue(script.contains("loadDemoData"));
            assertTrue(script.contains("load-demo-data-btn"));
            assertTrue(script.contains("openJobDetail"));
            assertTrue(script.contains("retryActiveJob"));
            assertTrue(script.contains("refreshKpisAfterUpdate"));
            assertTrue(script.contains("refreshKpisAfterUpdate: false"));
            assertTrue(script.contains("const demoHistory = data.history.slice(0, 12)"));
            assertTrue(script.contains("submittedAt: new Date().toISOString()"));
            assertTrue(script.contains("/retry"));
            assertTrue(script.contains("X-Clearfolio-Operator-Id"));
            assertTrue(script.contains("X-Clearfolio-Tenant-Id"));
            assertTrue(script.contains("X-Clearfolio-Permissions"));
            assertTrue(script.contains("deadLettered"));
        }
    }

    @Test
    void demoFixtureProvidesBuyerDemoStoryStates() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/assets/viewer/demo-fixtures.json")) {
            assertNotNull(input);
            JsonNode root = new ObjectMapper().readTree(input);
            JsonNode jobs = root.path("history");

            assertTrue(jobs.isArray());
            assertTrue(jobs.size() >= 4);
            assertTrue(root.path("kpiSnapshot").path("totalJobs").asInt() >= jobs.size());
            assertTrue(root.path("kpiSnapshot").path("conversionSuccessRate").asDouble() > 0.0);
            assertTrue(root.path("kpiExports").isArray());
            assertTrue(root.path("kpiExports").size() >= 1);
            assertTrue(root.toString().contains("SUCCEEDED"));
            assertTrue(root.toString().contains("UNSUPPORTED_FORMAT"));
            assertTrue(root.toString().contains("deadLettered"));
            for (JsonNode job : jobs) {
                assertDoesNotThrow(() -> Instant.parse(job.path("submittedAt").asText()));
            }
        }
    }

    @Test
    void viewerReturnsLoadingHtmlForAnyUuid() {
        UUID docId = UUID.randomUUID();

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-initial-state\" content=\"LOADING\"")));
    }

    @Test
    void viewerReturnsHtmlWithDocIdMeta() {
        UUID docId = UUID.randomUUID();

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId)));
    }

    @Test
    void viewerRejectsNonUuidDocIdWithFriendlyShell() {
        webTestClient.get()
                .uri("/viewer/not-a-uuid")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("clearfolio-doc-id"));
                    assertTrue(body.contains("not-a-uuid"));
                    assertTrue(body.contains("clearfolio-initial-state\" content=\"NOT_FOUND"));
                    assertTrue(body.contains("/assets/viewer/viewer.js"));
                });
    }

    @Test
    void viewerEscapesHtmlInInvalidDocId() {
        webTestClient.get()
                .uri("/viewer/{docId}", "\"><script>alert(1)</script>")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(!body.contains("<script>alert(1)</script>"));
                    assertTrue(body.contains("&lt;script&gt;"));
                });
    }

    @Test
    void viewerStillServesUuidDocId() {
        UUID docId = UUID.randomUUID();
        webTestClient.get()
                .uri("/viewer/" + docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains(docId.toString())));
    }
}
