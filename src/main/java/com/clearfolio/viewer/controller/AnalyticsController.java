package com.clearfolio.viewer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clearfolio.viewer.api.KpiSnapshotResponse;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Read-only analytics endpoints for buyer-demo and diligence evidence.
 */
@RestController
public class AnalyticsController {

    private final ConversionJobRepository repository;

    /**
     * Creates an analytics controller backed by the conversion job repository.
     *
     * @param repository conversion job repository
     */
    public AnalyticsController(ConversionJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns current conversion KPI counters.
     *
     * @return KPI snapshot payload
     */
    @GetMapping("/api/v1/analytics/kpi-snapshot")
    public KpiSnapshotResponse kpiSnapshot() {
        return KpiSnapshotResponse.from(repository.findAll());
    }
}
