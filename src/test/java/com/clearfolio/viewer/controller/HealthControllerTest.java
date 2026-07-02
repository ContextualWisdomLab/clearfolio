package com.clearfolio.viewer.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthEndpointReturnsOkAndPayload() {
        final HealthController controller = new HealthController();

        final Map<String, String> response = controller.health();

        assertThat(response).containsEntry("status", "ok");
    }
}
