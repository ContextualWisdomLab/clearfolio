package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

class AnalyticsControllerTest {

    private InMemoryConversionJobRepository repository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConversionJobRepository();
        webTestClient = WebTestClient.bindToController(new AnalyticsController(repository)).build();
    }

    @Test
    void kpiSnapshotReturnsZeroMetricsWhenNoJobsExist() {
        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalJobs").isEqualTo(0)
                .jsonPath("$.submittedJobs").isEqualTo(0)
                .jsonPath("$.processingJobs").isEqualTo(0)
                .jsonPath("$.succeededJobs").isEqualTo(0)
                .jsonPath("$.failedJobs").isEqualTo(0)
                .jsonPath("$.deadLetteredJobs").isEqualTo(0)
                .jsonPath("$.conversionSuccessRate").isEqualTo(0.0)
                .jsonPath("$.p95TimeToPreviewMs").isEmpty();
    }

    @Test
    void kpiSnapshotSummarizesCurrentJobStates() {
        ConversionJob submitted = newJob("submitted.docx");
        ConversionJob processing = newJob("processing.docx");
        ConversionJob succeeded = newJob("succeeded.docx");
        ConversionJob failed = newJob("failed.docx");

        processing.markProcessing("conversion started");
        succeeded.markProcessing("conversion started");
        succeeded.markSucceeded("/artifacts/" + succeeded.getJobId() + ".pdf", "done");
        failed.markDeadLettered("retries exhausted");

        repository.save(submitted);
        repository.save(processing);
        repository.save(succeeded);
        repository.save(failed);

        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalJobs").isEqualTo(4)
                .jsonPath("$.submittedJobs").isEqualTo(1)
                .jsonPath("$.processingJobs").isEqualTo(1)
                .jsonPath("$.succeededJobs").isEqualTo(1)
                .jsonPath("$.failedJobs").isEqualTo(1)
                .jsonPath("$.deadLetteredJobs").isEqualTo(1)
                .jsonPath("$.conversionSuccessRate").value(value -> assertEquals(0.25, (Double) value))
                .jsonPath("$.p95TimeToPreviewMs").value(value -> {
                    assertNotNull(value);
                    assertTrue(((Number) value).longValue() >= 0L);
                });
    }

    private ConversionJob newJob(String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                fileName,
                "application/octet-stream",
                fileName + "-hash",
                42L,
                3
        );
    }
}
