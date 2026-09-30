package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCredentialRegistryAdapterTest {

    @Test
    void getCredentialReturnsEmptyWhenPathIsMissingOrNull() {
        FileCredentialRegistryAdapter nullAdapter = new FileCredentialRegistryAdapter(null);
        FileCredentialRegistryAdapter blankAdapter = new FileCredentialRegistryAdapter("   ");

        assertTrue(nullAdapter.getCredential("some.key").isEmpty());
        assertTrue(blankAdapter.getCredential("some.key").isEmpty());
    }

    @Test
    void getCredentialReturnsEmptyWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.properties");
        FileCredentialRegistryAdapter adapter = new FileCredentialRegistryAdapter(missing.toString());

        assertTrue(adapter.getCredential("some.key").isEmpty());
    }

    @Test
    void getCredentialReturnsEmptyOnIoException(@TempDir Path tempDir) throws IOException {
        Path unreadable = tempDir.resolve("unreadable.properties");
        Files.createFile(unreadable);

        unreadable.toFile().setReadable(false, false);

        FileCredentialRegistryAdapter adapter = new FileCredentialRegistryAdapter(unreadable.toString());

        assertTrue(adapter.getCredential("some.key").isEmpty());

        unreadable.toFile().setReadable(true, false);
    }

    @Test
    void getCredentialResolvesKeyFromFile(@TempDir Path tempDir) throws IOException {
        Path registry = tempDir.resolve("registry.properties");
        Properties props = new Properties();
        props.setProperty("test.secret.key", "my-secret-value");
        try (var out = Files.newOutputStream(registry)) {
            props.store(out, null);
        }

        FileCredentialRegistryAdapter adapter = new FileCredentialRegistryAdapter(registry.toString());

        Optional<String> secret = adapter.getCredential("test.secret.key");
        assertTrue(secret.isPresent());
        assertEquals("my-secret-value", secret.get());
    }

    @Test
    void getCredentialReturnsEmptyForUnknownKeyInFile(@TempDir Path tempDir) throws IOException {
        Path registry = tempDir.resolve("registry.properties");
        Properties props = new Properties();
        props.setProperty("test.secret.key", "my-secret-value");
        try (var out = Files.newOutputStream(registry)) {
            props.store(out, null);
        }

        FileCredentialRegistryAdapter adapter = new FileCredentialRegistryAdapter(registry.toString());

        assertTrue(adapter.getCredential("unknown.key").isEmpty());
    }
}
