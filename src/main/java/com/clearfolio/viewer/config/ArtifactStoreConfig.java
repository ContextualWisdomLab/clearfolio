package com.clearfolio.viewer.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.FileSystemArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.artifact.LifecycleFencedArtifactStore;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionReceiptStore;
import com.clearfolio.viewer.lifecycle.ArtifactLifecycleLockRegistry;

/**
 * Configures the artifact store implementation used by conversion and serving.
 */
@Configuration
public class ArtifactStoreConfig {

    /**
     * Creates the Spring configuration component for artifact storage.
     */
    public ArtifactStoreConfig() {
        // Spring creates this stateless configuration object before invoking its bean method.
    }

    /**
     * Creates the selected artifact store behind a lifecycle fence.
     *
     * <p>The disk-backed delegate is the default so artifacts survive restart.
     * The wrapper serializes artifact operations with deletion and rejects writes
     * after durable deletion intent exists.</p>
     *
     * @param artifactStoreProperties artifact store configuration values
     * @param receiptStore durable artifact deletion receipt store
     * @param lifecycleLocks per-job conversion/deletion serialization boundary
     * @return configured deletion-aware artifact store
     */
    @Bean
    public ArtifactStore artifactStore(
            ArtifactStoreProperties artifactStoreProperties,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactLifecycleLockRegistry lifecycleLocks
    ) {
        return new LifecycleFencedArtifactStore(
                createDelegate(artifactStoreProperties),
                receiptStore,
                lifecycleLocks
        );
    }

    /**
     * Creates the historical standalone artifact-store selection without
     * deletion integration.
     *
     * <p>This overload preserves source compatibility for callers that construct
     * the configuration directly. Standalone applications that enable durable
     * deletion should call the three-argument factory so the artifact store and
     * deletion coordinator share one receipt store and lifecycle-lock registry.</p>
     *
     * @param artifactStoreProperties artifact store configuration values
     * @return selected filesystem or in-memory artifact store
     */
    public ArtifactStore artifactStore(ArtifactStoreProperties artifactStoreProperties) {
        return createDelegate(artifactStoreProperties);
    }

    private ArtifactStore createDelegate(ArtifactStoreProperties artifactStoreProperties) {
        if (artifactStoreProperties.isInMemoryMode()) {
            return new InMemoryArtifactStore();
        }
        return new FileSystemArtifactStore(Path.of(artifactStoreProperties.getRootDir()));
    }
}
