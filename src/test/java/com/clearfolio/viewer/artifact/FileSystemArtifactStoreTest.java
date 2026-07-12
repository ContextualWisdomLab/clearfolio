package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putAndGetRoundTripsPdfBytes() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(tempDir.resolve("artifacts"));
        UUID docId = UUID.randomUUID();
        byte[] original = "%PDF-1.7\noriginal-sheet-music".getBytes(StandardCharsets.UTF_8);

        store.putPdf(docId, original);

        byte[] stored = store.getPdf(docId).orElseThrow();
        assertArrayEquals(original, stored);
        assertEquals("%PDF-", new String(stored, 0, 5, StandardCharsets.UTF_8));
    }

    @Test
    void storedBytesSurviveStoreReinstantiation() {
        Path root = tempDir.resolve("artifacts");
        UUID docId = UUID.randomUUID();
        byte[] original = "%PDF-1.4\npersisted-artifact".getBytes(StandardCharsets.UTF_8);

        new FileSystemArtifactStore(root).putPdf(docId, original);

        FileSystemArtifactStore reopened = new FileSystemArtifactStore(root);
        byte[] reloaded = reopened.getPdf(docId).orElseThrow();
        assertArrayEquals(original, reloaded);
        assertEquals("%PDF-", new String(reloaded, 0, 5, StandardCharsets.UTF_8));

        byte[] cachedRead = reopened.getPdf(docId).orElseThrow();
        assertArrayEquals(original, cachedRead);
    }

    @Test
    void putPdfWritesMinimalMetadataSidecar() throws Exception {
        Path root = tempDir.resolve("artifacts");
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        UUID docId = UUID.randomUUID();
        byte[] original = "%PDF-1.7\nmetadata-check".getBytes(StandardCharsets.UTF_8);

        store.putPdf(docId, original);

        Path metadataPath = root.resolve(docId + ".meta.properties");
        String metadata = Files.readString(metadataPath, StandardCharsets.UTF_8);
        assertTrue(metadata.contains("docId=" + docId));
        assertTrue(metadata.contains("sizeBytes=" + original.length));
        assertTrue(metadata.contains("sha256="));
        assertTrue(metadata.contains("storedAt="));
    }

    @Test
    void deletePdfRemovesCachedBytesAndStoredFiles() throws Exception {
        Path root = tempDir.resolve("artifacts");
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        UUID docId = UUID.randomUUID();
        byte[] original = "%PDF-1.7\ndelete-me".getBytes(StandardCharsets.UTF_8);

        store.putPdf(docId, original);
        assertArrayEquals(original, store.getPdf(docId).orElseThrow());

        store.deletePdf(docId);

        assertEquals(Optional.empty(), store.getPdf(docId));
        assertTrue(Files.notExists(root.resolve(docId + ".pdf")));
        assertTrue(Files.notExists(root.resolve(docId + ".meta.properties")));
    }

    @Test
    void getPdfReturnsEmptyWhenArtifactIsMissing() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(tempDir.resolve("artifacts"));

        assertEquals(Optional.empty(), store.getPdf(UUID.randomUUID()));
    }

    @Test
    void returnedBytesAreIsolatedFromInternalState() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(tempDir.resolve("artifacts"));
        UUID docId = UUID.randomUUID();
        byte[] original = "%PDF-1.7\nisolation".getBytes(StandardCharsets.UTF_8);

        store.putPdf(docId, original);
        original[0] = 'X';

        byte[] first = store.getPdf(docId).orElseThrow();
        assertEquals('%', first[0]);

        first[0] = 'Y';
        byte[] second = store.getPdf(docId).orElseThrow();
        assertEquals('%', second[0]);
    }

    @Test
    void constructorThrowsWhenRootDirectoryCannotBeCreated() throws Exception {
        Path blockingFile = tempDir.resolve("blocking-file");
        Files.writeString(blockingFile, "not-a-directory");
        Path invalidRoot = blockingFile.resolve("artifacts");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new FileSystemArtifactStore(invalidRoot)
        );
        assertTrue(error.getMessage().contains("failed to create artifact storage directory"));
    }

    @Test
    void putPdfWrapsWriteFailures() {
        UUID docId = UUID.randomUUID();
        FileSystemArtifactStore store = new FileSystemArtifactStore(
                tempDir.resolve("artifacts"),
                (path, bytes) -> {
                    throw new IOException("disk full");
                },
                Files::readAllBytes
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> store.putPdf(docId, "%PDF-1.7".getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(error.getMessage().contains("failed to persist artifact for docId " + docId));
        assertEquals(Optional.empty(), store.getPdf(docId));
    }

    @Test
    void getPdfWrapsReadFailures() {
        Path root = tempDir.resolve("artifacts");
        UUID docId = UUID.randomUUID();
        new FileSystemArtifactStore(root).putPdf(docId, "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        FileSystemArtifactStore failingReadStore = new FileSystemArtifactStore(
                root,
                (path, bytes) -> Files.write(path, bytes),
                path -> {
                    throw new IOException("read denied");
                }
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> failingReadStore.getPdf(docId)
        );
        assertTrue(error.getMessage().contains("failed to read artifact for docId " + docId));
    }

    @Test
    void putPdfThrowsWhenSha256DigestIsUnavailable() {
        FileSystemArtifactStore store = new FileSystemArtifactStore(tempDir.resolve("artifacts"));

        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> store.putPdf(UUID.randomUUID(), "%PDF-1.7".getBytes(StandardCharsets.UTF_8))
            );
            assertEquals("SHA-256 digest unavailable", error.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }
    }
}
