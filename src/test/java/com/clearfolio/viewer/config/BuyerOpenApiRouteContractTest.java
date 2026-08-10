package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        Map<?, ?> root = loadOpenApi();
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

    @Test
    void openApiDescribesProtectedMainSignedArtifactByteRoute() throws IOException {
        Map<?, ?> root = loadOpenApi();
        Map<?, ?> paths = assertInstanceOf(Map.class, root.get("paths"));
        Map<?, ?> artifactPath = assertInstanceOf(Map.class, paths.get("/artifacts/{docId}.pdf"));
        Map<?, ?> getOperation = assertInstanceOf(Map.class, artifactPath.get("get"));

        assertEquals("getPdfArtifact", getOperation.get("operationId"));
        assertNotNull(getOperation.get("parameters"));
        assertInstanceOf(List.class, getOperation.get("security"));

        Map<?, ?> responses = assertInstanceOf(Map.class, getOperation.get("responses"));
        for (String status : List.of("200", "206", "401", "403", "404", "416")) {
            assertTrue(responses.containsKey(status), "missing artifact response " + status);
        }

        Map<?, ?> components = assertInstanceOf(Map.class, root.get("components"));
        Map<?, ?> securitySchemes = assertInstanceOf(Map.class, components.get("securitySchemes"));
        Map<?, ?> queryScheme = assertInstanceOf(Map.class, securitySchemes.get("artifactTokenQuery"));
        assertEquals("apiKey", queryScheme.get("type"));
        assertEquals("query", queryScheme.get("in"));
        assertEquals("artifactToken", queryScheme.get("name"));

        Map<?, ?> bearerScheme = assertInstanceOf(Map.class, securitySchemes.get("artifactTokenBearer"));
        assertEquals("http", bearerScheme.get("type"));
        assertEquals("bearer", bearerScheme.get("scheme"));
    }

    private static Map<?, ?> loadOpenApi() throws IOException {
        Object parsed;
        try (Reader reader = Files.newBufferedReader(OPENAPI_PATH)) {
            parsed = new Yaml().load(reader);
        }
        return assertInstanceOf(Map.class, parsed);
    }
}
