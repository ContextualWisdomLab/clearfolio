package com.clearfolio.viewer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import com.clearfolio.viewer.api.KpiSnapshotExportResponse;
import com.clearfolio.viewer.api.KpiSnapshotResponse;
import com.clearfolio.viewer.analytics.KpiSnapshotLedger;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Read-only analytics endpoints for buyer-demo and diligence evidence.
 */
@RestController
public class AnalyticsController {

    private final ConversionJobRepository repository;
    private final TenantAccessService tenantAccessService;
    private final KpiSnapshotLedger snapshotLedger;

    /**
     * Creates an analytics controller backed by the conversion job repository.
     *
     * @param repository conversion job repository
     * @param tenantAccessService tenant and permission guard
     * @param snapshotLedger KPI snapshot evidence ledger
     */
    public AnalyticsController(
            ConversionJobRepository repository,
            TenantAccessService tenantAccessService,
            KpiSnapshotLedger snapshotLedger) {
        this.repository = repository;
        this.tenantAccessService = tenantAccessService;
        this.snapshotLedger = snapshotLedger;
    }

    /**
     * Returns current conversion KPI counters.
     *
     * @param headers request headers carrying tenant claims
     * @return KPI snapshot payload
     */
    @GetMapping("/api/v1/analytics/kpi-snapshot")
    public KpiSnapshotResponse kpiSnapshot(@RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.ANALYTICS_READ);
        KpiSnapshotResponse snapshot = KpiSnapshotResponse.from(repository.findAll().stream()
                .filter(job -> job.belongsToTenant(tenantContext.tenantId()))
                .toList());
        snapshotLedger.recordSnapshot(tenantContext, snapshot);
        return snapshot;
    }

    /**
     * Returns exported KPI snapshot evidence for the request tenant.
     *
     * @param headers request headers carrying tenant claims
     * @return exported KPI snapshot evidence
     */
    @GetMapping("/api/v1/analytics/kpi-snapshot-exports")
    public List<KpiSnapshotExportResponse> kpiSnapshotExports(@RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.ANALYTICS_READ);
        return snapshotLedger.snapshotsFor(tenantContext.tenantId()).stream()
                .map(KpiSnapshotExportResponse::from)
                .toList();
    }
}
