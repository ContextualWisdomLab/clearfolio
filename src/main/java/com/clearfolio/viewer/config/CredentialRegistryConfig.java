package com.clearfolio.viewer.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.DirectoryCredentialRegistry;

/**
 * Creates the provider-neutral credential registry used by production security consumers.
 */
@Configuration
@Profile("production")
public class CredentialRegistryConfig {

    /**
     * Creates the stateless Spring configuration component.
     */
    public CredentialRegistryConfig() {
        // Spring invokes the bean factory after loading the production profile.
    }

    /**
     * Opens the read-only directory registry selected by the bootstrap location.
     *
     * <p>The configured value identifies a mounted credential directory; it is
     * not key material. Missing, blank, non-directory, or symlinked roots fail
     * production startup instead of falling back to a legacy signing property.</p>
     *
     * @param rootDirectory mounted credential-registry root
     * @return production credential registry
     * @throws IllegalStateException when the configured root is unavailable
     */
    @Bean
    public CredentialRegistry credentialRegistry(
            @Value("${clearfolio.credential-registry.root-directory:}") String rootDirectory) {
        if (rootDirectory == null || rootDirectory.isBlank()) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.credential-registry.root-directory"
            );
        }
        try {
            return new DirectoryCredentialRegistry(Path.of(rootDirectory));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("production credential registry unavailable", exception);
        }
    }
}
