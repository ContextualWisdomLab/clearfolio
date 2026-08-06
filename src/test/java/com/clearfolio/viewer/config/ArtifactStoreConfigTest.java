package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.LifecycleFencedArtifactStore;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionLedger;
import com.clearfolio.viewer.lifecycle.ArtifactLifecycleLockRegistry;

/**
 * Verifies artifact-store mode selection behind the deletion lifecycle fence.
 */
class ArtifactStoreConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void filesystemModeCreatesRestartDurableFencedStore() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootDir(tempDir.resolve("artifacts").toString());
        UUID docId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        ArtifactStore firstStore = configuredStore(properties);

        assertInstanceOf(LifecycleFencedArtifactStore.class, firstStore);
        firstStore.putPdf(docId, bytes);

        ArtifactStore restartedStore = configuredStore(properties);
        assertArrayEquals(bytes, restartedStore.getPdf(docId).orElseThrow());
    }

    @Test
    void inMemoryModeCreatesVolatileFencedStore() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setMode(ArtifactStoreProperties.MODE_IN_MEMORY);
        UUID docId = UUID.randomUUID();
        ArtifactStore firstStore = configuredStore(properties);
        firstStore.putPdf(docId, new byte[] {1});

        ArtifactStore restartedStore = configuredStore(properties);

        assertInstanceOf(LifecycleFencedArtifactStore.class, restartedStore);
        assertTrue(restartedStore.getPdf(docId).isEmpty());
    }

    private static ArtifactStore configuredStore(ArtifactStoreProperties properties) {
        return new ArtifactStoreConfig().artifactStore(
                properties,
                new ArtifactDeletionLedger(),
                new ArtifactLifecycleLockRegistry()
        );
    }
}
