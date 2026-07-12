package com.clearfolio.viewer.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.FileSystemArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;

/**
 * Configures the artifact store implementation used by conversion and serving.
 */
@Configuration
public class ArtifactStoreConfig {

    /**
     * Creates the artifact store selected by configuration; the disk-backed
     * store is the default so artifacts survive application restarts.
     *
     * @param artifactStoreProperties artifact store configuration values
     * @return configured artifact store
     */
    @Bean
    public ArtifactStore artifactStore(ArtifactStoreProperties artifactStoreProperties) {
        if (artifactStoreProperties.isInMemoryMode()) {
            return new InMemoryArtifactStore();
        }
        return new FileSystemArtifactStore(Path.of(artifactStoreProperties.getRootDir()));
    }
}
