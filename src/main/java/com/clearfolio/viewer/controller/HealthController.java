package com.clearfolio.viewer.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes separate liveness and readiness probes on the application port.
 *
 * <p>Liveness answers whether this process can continue operating or needs a
 * restart. Readiness answers whether the instance should receive traffic. The
 * two signals deliberately remain separate so a temporary readiness failure
 * does not trigger a restart cascade.</p>
 */
@RestController
public class HealthController {

    private final ApplicationAvailability applicationAvailability;

    /**
     * Creates the probe controller from Spring Boot's availability state.
     *
     * @param applicationAvailability current application availability provider
     */
    public HealthController(ApplicationAvailability applicationAvailability) {
        this.applicationAvailability = Objects.requireNonNull(
                applicationAvailability,
                "applicationAvailability"
        );
    }

    /**
     * Returns the process liveness state.
     *
     * @return {@code 200} with {@code status=ok} when the process can recover,
     *         otherwise {@code 503} with {@code status=broken}
     */
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> liveness() {
        return availabilityResponse(
                applicationAvailability.getLivenessState() == LivenessState.CORRECT,
                "ok",
                "broken"
        );
    }

    /**
     * Returns whether this instance is ready to accept traffic.
     *
     * @return {@code 200} with {@code status=ready} while accepting traffic,
     *         otherwise {@code 503} with {@code status=not_ready}
     */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readiness() {
        return availabilityResponse(
                applicationAvailability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC,
                "ready",
                "not_ready"
        );
    }

    private static ResponseEntity<Map<String, String>> availabilityResponse(
            boolean available,
            String availableStatus,
            String unavailableStatus
    ) {
        HttpStatus responseStatus = available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        String statusValue = available ? availableStatus : unavailableStatus;
        return ResponseEntity.status(responseStatus)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", statusValue));
    }
}
