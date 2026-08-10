package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that liveness and readiness expose different operational states.
 */
class HealthControllerTest {

    @Test
    void livenessReturnsOkWhenTheApplicationCanRecover() {
        ApplicationAvailability availability = availability(
                LivenessState.CORRECT,
                ReadinessState.ACCEPTING_TRAFFIC
        );

        client(availability).get()
                .uri("/healthz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class)
                .isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void livenessReturnsServiceUnavailableForAnUnrecoverableApplication() {
        ApplicationAvailability availability = availability(
                LivenessState.BROKEN,
                ReadinessState.ACCEPTING_TRAFFIC
        );

        client(availability).get()
                .uri("/healthz")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class)
                .isEqualTo("{\"status\":\"broken\"}");
    }

    @Test
    void readinessReturnsOkOnlyWhileTrafficCanBeAccepted() {
        ApplicationAvailability availability = availability(
                LivenessState.CORRECT,
                ReadinessState.ACCEPTING_TRAFFIC
        );

        client(availability).get()
                .uri("/readyz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class)
                .isEqualTo("{\"status\":\"ready\"}");
    }

    @Test
    void readinessReturnsServiceUnavailableWhileTrafficIsRefused() {
        ApplicationAvailability availability = availability(
                LivenessState.CORRECT,
                ReadinessState.REFUSING_TRAFFIC
        );

        client(availability).get()
                .uri("/readyz")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class)
                .isEqualTo("{\"status\":\"not_ready\"}");
    }

    @Test
    void controllerRejectsMissingAvailabilityStateProvider() {
        assertThrows(NullPointerException.class, () -> new HealthController(null));
    }

    private static ApplicationAvailability availability(
            LivenessState livenessState,
            ReadinessState readinessState
    ) {
        ApplicationAvailability availability = mock(ApplicationAvailability.class);
        when(availability.getLivenessState()).thenReturn(livenessState);
        when(availability.getReadinessState()).thenReturn(readinessState);
        return availability;
    }

    private static WebTestClient client(ApplicationAvailability availability) {
        return WebTestClient.bindToController(new HealthController(availability)).build();
    }
}
