package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Verifies that the service persistence boundary rejects unsafe filenames even
 * when a replaceable validation adapter does not enforce the same rule.
 */
class DefaultDocumentConversionServiceCoverageTest {

    @Test
    void rejectsNullByteFilenameAtTheServiceBoundaryWithAPluggableValidator() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        DocumentValidationService permissiveValidator = file -> {
            // Deliberately permissive to prove the service has its own boundary.
        };
        ConversionWorker worker = jobId -> {
            throw new AssertionError("unsafe upload must not be enqueued");
        };
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                permissiveValidator,
                worker,
                new InMemoryArtifactStore(),
                new ConversionProperties()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unsafe\u0000.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(file)
        );

        assertEquals("File name contains null byte.", exception.getMessage());
        assertEquals(0, repository.findAll().size());
    }
}
