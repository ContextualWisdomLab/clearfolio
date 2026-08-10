package com.clearfolio.viewer.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight endpoint used for process-liveness checks.
 *
 * <p>This endpoint deliberately reports only whether the application process can
 * answer requests. Traffic-readiness semantics are introduced separately so an
 * orchestrator never confuses restart eligibility with dependency readiness.</p>
 */
@RestController
@RequestMapping("/healthz")
public class HealthController {

    /**
     * Creates the stateless liveness controller.
     */
    public HealthController() {
        // No mutable state or external dependency belongs in the liveness path.
    }

    /**
     * Returns a static health payload when the service is alive.
     *
     * @return health status payload
     */
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
