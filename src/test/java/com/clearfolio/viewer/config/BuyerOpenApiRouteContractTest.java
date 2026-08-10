package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifies buyer-facing OpenAPI coverage for public routes already shipped on protected main.
 */
class BuyerOpenApiRouteContractTest {

    private static final Path OPENAPI_PATH =
            Path.of("docs/deployment/clearfolio-buyer-connector.openapi.yaml");

    @Test
    void openApiDescribesProtectedMainConversionJobDeleteRoute() throws IOException {
        Object parsed;
        try (Reader reader = Files.newBufferedReader(OPENAPI_PATH)) {
            parsed = new Yaml().load(reader);
        }

        Map<?, ?> root = assertInstanceOf(Map.class, parsed);
        Map<?, ?> paths = assertInstanceOf(Map.class, root.get("paths"));
        Map<?, ?> jobPath = assertInstanceOf(Map.class, paths.get("/api/v1/convert/jobs/{jobId}"));
        Map<?, ?> deleteOperation = assertInstanceOf(Map.class, jobPath.get("delete"));

        assertEquals("deleteConversionJob", deleteOperation.get("operationId"));
        assertTrue(String.valueOf(deleteOperation.get("description")).contains("job:delete"));
        assertNotNull(deleteOperation.get("parameters"));

        Map<?, ?> responses = assertInstanceOf(Map.class, deleteOperation.get("responses"));
        assertTrue(responses.containsKey("204"));
        assertTrue(responses.containsKey("401"));
        assertTrue(responses.containsKey("403"));
        assertTrue(responses.containsKey("404"));
    }
}
