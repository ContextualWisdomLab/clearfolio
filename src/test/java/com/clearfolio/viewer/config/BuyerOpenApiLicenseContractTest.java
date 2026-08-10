package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifies acquisition-facing OpenAPI license metadata through the parsed YAML structure.
 */
class BuyerOpenApiLicenseContractTest {

    private static final Path OPENAPI_PATH =
            Path.of("docs/deployment/clearfolio-buyer-connector.openapi.yaml");
    private static final Path LICENSE_PATH = Path.of("LICENSE");

    @Test
    void openApiLicenseObjectMatchesRepositoryAuthority() throws IOException {
        String repositoryLicense = Files.readString(LICENSE_PATH);
        assertTrue(repositoryLicense.startsWith("Apache License\nVersion 2.0"));

        Object parsed;
        try (Reader reader = Files.newBufferedReader(OPENAPI_PATH)) {
            parsed = new Yaml().load(reader);
        }

        Map<?, ?> root = assertInstanceOf(Map.class, parsed);
        Map<?, ?> info = assertInstanceOf(Map.class, root.get("info"));
        Map<?, ?> license = assertInstanceOf(Map.class, info.get("license"));

        assertEquals("Apache-2.0", license.get("name"));
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0.html", license.get("url"));
    }
}
