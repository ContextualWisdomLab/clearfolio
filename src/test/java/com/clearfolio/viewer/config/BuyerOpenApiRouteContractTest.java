package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        assertHasParameter(root, deleteOperation, "jobId", "path", true);

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
        assertHasParameter(root, getOperation, "docId", "path", true);
        assertHasParameter(root, getOperation, "Range", "header", false);
        assertEquals(
                List.of(
                        Map.of("artifactTokenQuery", List.of()),
                        Map.of("artifactTokenBearer", List.of())),
                getOperation.get("security"));

        Map<?, ?> responses = assertInstanceOf(Map.class, getOperation.get("responses"));
        for (String status : List.of("200", "206", "401", "403", "404", "416")) {
            assertTrue(responses.containsKey(status), "missing artifact response " + status);
        }
        assertContentRangeHeader(responses, "206", "bytes start-end/total");
        assertContentRangeHeader(responses, "416", "bytes */total");

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

    @Test
    void openApiDescribesUnauthenticatedProtectedMainLivenessRoute() throws IOException {
        Map<?, ?> root = loadOpenApi();
        Map<?, ?> paths = assertInstanceOf(Map.class, root.get("paths"));
        Map<?, ?> healthPath = assertInstanceOf(Map.class, paths.get("/healthz"));
        Map<?, ?> getOperation = assertInstanceOf(Map.class, healthPath.get("get"));

        assertEquals("getHealthz", getOperation.get("operationId"));
        assertEquals(List.of(), getOperation.get("security"));

        Map<?, ?> responses = assertInstanceOf(Map.class, getOperation.get("responses"));
        Map<?, ?> okResponse = assertInstanceOf(Map.class, responses.get("200"));
        Map<?, ?> content = assertInstanceOf(Map.class, okResponse.get("content"));
        Map<?, ?> jsonContent = assertInstanceOf(Map.class, content.get("application/json"));
        Map<?, ?> schema = assertInstanceOf(Map.class, jsonContent.get("schema"));
        assertEquals("#/components/schemas/HealthResponse", schema.get("$ref"));
    }

    private static void assertHasParameter(
            Map<?, ?> root,
            Map<?, ?> operation,
            String expectedName,
            String expectedLocation,
            boolean expectedRequired
    ) {
        List<?> parameters = assertInstanceOf(List.class, operation.get("parameters"));
        assertTrue(
                parameters.stream()
                        .map(parameter -> resolveParameter(root, parameter))
                        .anyMatch(parameter -> expectedName.equals(parameter.get("name"))
                                && expectedLocation.equals(parameter.get("in"))
                                && Boolean.valueOf(expectedRequired).equals(parameter.get("required"))),
                "missing parameter " + expectedName + " in " + expectedLocation
        );
    }

    private static void assertContentRangeHeader(
            Map<?, ?> responses,
            String status,
            String expectedDescriptionFragment
    ) {
        Map<?, ?> response = assertInstanceOf(Map.class, responses.get(status));
        Map<?, ?> headers = assertInstanceOf(Map.class, response.get("headers"));
        Map<?, ?> contentRange = assertInstanceOf(Map.class, headers.get("Content-Range"));
        Map<?, ?> schema = assertInstanceOf(Map.class, contentRange.get("schema"));
        assertEquals("string", schema.get("type"));
        assertTrue(
                String.valueOf(contentRange.get("description")).contains(expectedDescriptionFragment),
                "Content-Range description for " + status + " must explain " + expectedDescriptionFragment
        );
    }

    private static Map<?, ?> resolveParameter(Map<?, ?> root, Object parameterValue) {
        Map<?, ?> parameter = assertInstanceOf(Map.class, parameterValue);
        Object reference = parameter.get("$ref");
        if (reference == null) {
            return parameter;
        }

        String prefix = "#/components/parameters/";
        String referenceText = assertInstanceOf(String.class, reference);
        assertTrue(referenceText.startsWith(prefix), "unsupported parameter reference " + referenceText);

        Map<?, ?> components = assertInstanceOf(Map.class, root.get("components"));
        Map<?, ?> componentParameters = assertInstanceOf(Map.class, components.get("parameters"));
        return assertInstanceOf(Map.class, componentParameters.get(referenceText.substring(prefix.length())));
    }

    private static Map<?, ?> loadOpenApi() throws IOException {
        Object parsed;
        try (Reader reader = Files.newBufferedReader(OPENAPI_PATH)) {
            parsed = new Yaml().load(reader);
        }
        return assertInstanceOf(Map.class, parsed);
    }
}
