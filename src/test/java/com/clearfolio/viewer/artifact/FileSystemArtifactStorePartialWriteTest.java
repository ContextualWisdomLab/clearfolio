package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that a failed multi-file artifact write does not publish orphaned
 * PDF bytes without the matching metadata sidecar.
 */
class FileSystemArtifactStorePartialWriteTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void metadataWriteFailureRollsBackNewArtifactBytes() throws Exception {
        Path root = temporaryDirectory.resolve("artifacts");
        UUID docId = UUID.randomUUID();
        FileSystemArtifactStore store = new FileSystemArtifactStore(
                root,
                (path, bytes) -> {
                    if (path.getFileName().toString().endsWith(".meta.properties")) {
                        throw new IOException("metadata disk full");
                    }
                    Files.write(path, bytes);
                },
                Files::readAllBytes
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> store.putPdf(docId, "%PDF-1.7\npartial-write".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals("failed to persist artifact for docId " + docId, error.getMessage());
        assertTrue(Files.notExists(root.resolve(docId + ".pdf")));
        assertTrue(Files.notExists(root.resolve(docId + ".meta.properties")));
        assertEquals(Optional.empty(), store.getPdf(docId));
    }

    @Test
    void rollbackFailureIsRetainedAsSuppressedEvidence() throws Exception {
        Path root = temporaryDirectory.resolve("rollback-failure");
        UUID docId = UUID.randomUUID();
        FileSystemArtifactStore store = new FileSystemArtifactStore(
                root,
                (path, bytes) -> {
                    if (path.getFileName().toString().endsWith(".pdf")) {
                        Files.createDirectory(path);
                        Files.writeString(path.resolve("retained"), "evidence");
                        return;
                    }
                    throw new IOException("metadata disk full");
                },
                Files::readAllBytes
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> store.putPdf(docId, "%PDF-1.7\nrollback-failure".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals("failed to persist artifact for docId " + docId, error.getMessage());
        assertEquals(1, error.getCause().getSuppressed().length);
        assertTrue(Files.exists(root.resolve(docId + ".pdf").resolve("retained")));
    }
}
