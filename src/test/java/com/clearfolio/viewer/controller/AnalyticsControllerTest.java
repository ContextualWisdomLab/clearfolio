package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.analytics.KpiSnapshotLedger;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

class AnalyticsControllerTest {

    private InMemoryConversionJobRepository repository;
    private KpiSnapshotLedger snapshotLedger;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConversionJobRepository();
        snapshotLedger = new KpiSnapshotLedger();
        webTestClient = WebTestClient.bindToController(
                new AnalyticsController(repository, new TenantAccessService(), snapshotLedger)
        ).controllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void kpiSnapshotReturnsZeroMetricsWhenNoJobsExist() {
        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .headers(AnalyticsControllerTest::addAnalyticsAuth)
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

        assertEquals(1, snapshotLedger.snapshotsFor(TenantContext.DEMO_TENANT_ID).size());
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
                .headers(AnalyticsControllerTest::addAnalyticsAuth)
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

        assertEquals(1, snapshotLedger.snapshotsFor(TenantContext.DEMO_TENANT_ID).size());
    }

    @Test
    void kpiSnapshotFiltersJobsToCurrentTenant() {
        repository.save(newJob("current-tenant.docx"));
        repository.save(new ConversionJob(
                UUID.randomUUID(),
                "tenant-b",
                "user-b",
                "other-tenant.docx",
                "application/octet-stream",
                "other-tenant-hash",
                42L,
                3
        ));

        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .headers(AnalyticsControllerTest::addAnalyticsAuth)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalJobs").isEqualTo(1)
                .jsonPath("$.submittedJobs").isEqualTo(1);

        assertEquals(1, snapshotLedger.snapshotsFor(TenantContext.DEMO_TENANT_ID).size());
    }

    @Test
    void kpiSnapshotRejectsMissingAnalyticsPermission() {
        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .headers(headers -> addAuth(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo("missing permission: " + TenantPermissions.ANALYTICS_READ);

        assertTrue(snapshotLedger.snapshotsFor(TenantContext.DEMO_TENANT_ID).isEmpty());
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

    private static void addAnalyticsAuth(HttpHeaders headers) {
        addAuth(headers, TenantPermissions.ANALYTICS_READ);
    }

    private static void addAuth(HttpHeaders headers, String permissions) {
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
    }
}
