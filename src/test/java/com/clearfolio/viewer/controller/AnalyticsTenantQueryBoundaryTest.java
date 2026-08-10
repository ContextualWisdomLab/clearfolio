package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.clearfolio.viewer.repository.ConversionJobRepository;

class AnalyticsTenantQueryBoundaryTest {

    private ConversionJobRepository repository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        repository = mock(ConversionJobRepository.class);
        webTestClient = WebTestClient.bindToController(new AnalyticsController(
                repository,
                new TenantAccessService(),
                new KpiSnapshotLedger()
        )).controllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void kpiSnapshotUsesStorageScopedTenantQueryInsteadOfGlobalInventory() {
        ConversionJob tenantJob = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "operator-a",
                "tenant-a.docx",
                "application/octet-stream",
                "tenant-a-hash",
                42L,
                3
        );
        when(repository.findAllByTenantId("tenant-a")).thenReturn(List.of(tenantJob));

        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .headers(AnalyticsTenantQueryBoundaryTest::addAnalyticsAuth)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalJobs").isEqualTo(1)
                .jsonPath("$.submittedJobs").isEqualTo(1);

        verify(repository).findAllByTenantId("tenant-a");
        verify(repository, never()).findAll();
    }

    @Test
    void missingAnalyticsPermissionDoesNotTouchEitherRepositoryQuery() {
        webTestClient.get()
                .uri("/api/v1/analytics/kpi-snapshot")
                .headers(headers -> addAuth(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden();

        verify(repository, never()).findAllByTenantId("tenant-a");
        verify(repository, never()).findAll();
    }

    private static void addAnalyticsAuth(HttpHeaders headers) {
        addAuth(headers, TenantPermissions.ANALYTICS_READ);
    }

    private static void addAuth(HttpHeaders headers, String permission) {
        headers.set(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.set(TenantContext.SUBJECT_ID_HEADER, "operator-a");
        headers.set(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
