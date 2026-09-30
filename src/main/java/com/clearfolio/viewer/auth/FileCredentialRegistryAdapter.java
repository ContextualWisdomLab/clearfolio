package com.clearfolio.viewer.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bridge adapter to resolve credentials from a local properties file.
 */
@Component
public class FileCredentialRegistryAdapter implements CredentialRegistryPort {

    private final Path registryPath;

    /**
     * Creates an adapter using the specified properties file path.
     *
     * @param registryPath path to the credentials properties file
     */
    public FileCredentialRegistryAdapter(@Value("${clearfolio.credential-registry.path:}") final String registryPath) {
        if (registryPath == null || registryPath.isBlank()) {
            this.registryPath = null;
        } else {
            this.registryPath = Path.of(registryPath);
        }
    }

    @Override
    public Optional<String> getCredential(final String name) {
        if (registryPath == null || !Files.isRegularFile(registryPath)) {
            return Optional.empty();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(registryPath)) {
            props.load(in);
            return Optional.ofNullable(props.getProperty(name));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
