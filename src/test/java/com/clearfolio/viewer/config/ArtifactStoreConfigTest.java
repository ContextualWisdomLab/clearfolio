package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.FileSystemArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;

class ArtifactStoreConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void filesystemModeCreatesDiskBackedStore() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootDir(tempDir.resolve("artifacts").toString());

        ArtifactStore store = new ArtifactStoreConfig().artifactStore(properties);

        assertInstanceOf(FileSystemArtifactStore.class, store);
        UUID docId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        store.putPdf(docId, bytes);
        assertArrayEquals(bytes, store.getPdf(docId).orElseThrow());
    }

    @Test
    void inMemoryModeCreatesVolatileStore() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setMode(ArtifactStoreProperties.MODE_IN_MEMORY);

        ArtifactStore store = new ArtifactStoreConfig().artifactStore(properties);

        assertInstanceOf(InMemoryArtifactStore.class, store);
    }
}
