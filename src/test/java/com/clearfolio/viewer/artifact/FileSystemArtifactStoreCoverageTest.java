package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that filesystem deletion failures remain visible to operators.
 */
class FileSystemArtifactStoreCoverageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deleteFailsClosedWhenTheArtifactPathCannotBeRemoved() throws Exception {
        FileSystemArtifactStore store = new FileSystemArtifactStore(temporaryDirectory);
        UUID docId = UUID.randomUUID();
        Path nonEmptyArtifactDirectory = temporaryDirectory.resolve(docId + ".pdf");
        Files.createDirectory(nonEmptyArtifactDirectory);
        Files.writeString(nonEmptyArtifactDirectory.resolve("child"), "retained");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.deletePdf(docId)
        );

        assertEquals("failed to delete artifact for docId " + docId, exception.getMessage());
    }
}
