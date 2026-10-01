package com.clearfolio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Locks dependency security floors that are required by the repository scanner.
 */
class SecurityDependencyFloorTest {

    /**
     * Prevents reintroducing Jackson releases affected by the September 2026
     * denial-of-service advisories.
     *
     * @throws IOException when the Maven project descriptor cannot be read
     */
    @Test
    void jacksonBomUsesPatchedSecurityRelease() throws IOException {
        String projectDescriptor = Files.readString(Path.of("pom.xml"));

        assertTrue(
                projectDescriptor.contains("<jackson-bom.version>2.22.3</jackson-bom.version>"),
                "jackson-bom must remain on the patched 2.22.3 release or newer");
    }
}
