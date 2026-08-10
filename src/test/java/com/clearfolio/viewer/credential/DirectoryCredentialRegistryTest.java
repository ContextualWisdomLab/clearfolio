package com.clearfolio.viewer.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryCredentialRegistryTest {

    private static final CredentialPurpose TENANT_PURPOSE = CredentialPurpose.TENANT_CLAIMS_SIGNING;

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesActivePurposeScopedCredentialWithoutExposingSourceBytes() throws IOException {
        byte[] storedSecret = new byte[] {7, 3, 1, 9};
        writeCredential(tempDirectory, "tenant-claims", TENANT_PURPOSE, "v7", storedSecret);

        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        CredentialSnapshot snapshot = registry.resolve(new CredentialReference("tenant-claims", TENANT_PURPOSE));

        assertEquals("tenant-claims", snapshot.credentialId());
        assertEquals("v7", snapshot.version());
        assertEquals(TENANT_PURPOSE, snapshot.purpose());
        assertArrayEquals(storedSecret, snapshot.secretBytes());

        storedSecret[0] = 0;
        assertArrayEquals(new byte[] {7, 3, 1, 9}, snapshot.secretBytes());
    }

    @Test
    void failsClosedForMissingRegistryOrReference() throws IOException {
        Path regularFile = tempDirectory.resolve("not-a-directory");
        Files.writeString(regularFile, "x", StandardCharsets.UTF_8);

        assertThrows(NullPointerException.class, () -> new DirectoryCredentialRegistry(null));
        assertThrows(IllegalArgumentException.class, () -> new DirectoryCredentialRegistry(regularFile));

        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        assertThrows(NullPointerException.class, () -> registry.resolve(null));
    }

    @Test
    void rejectsUnsafeCredentialNamesBeforePathResolution() {
        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);

        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                new CredentialReference("../escape", TENANT_PURPOSE)
        ));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                new CredentialReference("tenant/claims", TENANT_PURPOSE)
        ));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                new CredentialReference("tenant\\claims", TENANT_PURPOSE)
        ));
    }

    @Test
    void rejectsMissingMalformedOrMismatchedPurposeMetadata() throws IOException {
        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        Path credentialDirectory = Files.createDirectories(tempDirectory.resolve("tenant-claims"));
        Path purposePath = credentialDirectory.resolve("purpose");

        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.writeString(purposePath, "   ", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.writeString(purposePath, "NOT_A_PURPOSE", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.writeString(
                purposePath,
                CredentialPurpose.ARTIFACT_TOKEN_SIGNING.name(),
                StandardCharsets.UTF_8
        );
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));
    }

    @Test
    void rejectsMissingUnsafeOrNonCanonicalActiveVersion() throws IOException {
        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        Path credentialDirectory = Files.createDirectories(tempDirectory.resolve("tenant-claims"));
        Files.writeString(credentialDirectory.resolve("purpose"), TENANT_PURPOSE.name(), StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.writeString(credentialDirectory.resolve("active-version"), "../v1", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.writeString(credentialDirectory.resolve("active-version"), " v1 ", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));
    }

    @Test
    void rejectsMissingEmptyOrOversizedSecretMaterial() throws IOException {
        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        Path credentialDirectory = Files.createDirectories(tempDirectory.resolve("tenant-claims"));
        Files.writeString(credentialDirectory.resolve("purpose"), TENANT_PURPOSE.name(), StandardCharsets.UTF_8);
        Files.writeString(credentialDirectory.resolve("active-version"), "v1", StandardCharsets.UTF_8);
        Path versionsDirectory = Files.createDirectories(credentialDirectory.resolve("versions"));

        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Path secretPath = versionsDirectory.resolve("v1.key");
        Files.write(secretPath, new byte[0]);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.write(secretPath, new byte[(64 * 1024) + 1]);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));
    }

    @Test
    void rejectsOversizedMetadataAndSymlinkedCredentialDirectory() throws IOException {
        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        Path credentialDirectory = Files.createDirectories(tempDirectory.resolve("tenant-claims"));
        Files.writeString(
                credentialDirectory.resolve("purpose"),
                "X".repeat(257),
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));

        Files.delete(credentialDirectory.resolve("purpose"));
        Files.delete(credentialDirectory);
        Path outside = Files.createDirectories(tempDirectory.resolve("outside"));
        Files.createSymbolicLink(credentialDirectory, outside);

        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));
    }

    @Test
    void rejectsSymlinkedSecretMaterial() throws IOException {
        Path credentialDirectory = Files.createDirectories(tempDirectory.resolve("tenant-claims"));
        Files.writeString(credentialDirectory.resolve("purpose"), TENANT_PURPOSE.name(), StandardCharsets.UTF_8);
        Files.writeString(credentialDirectory.resolve("active-version"), "v1", StandardCharsets.UTF_8);
        Path versionsDirectory = Files.createDirectories(credentialDirectory.resolve("versions"));
        Path outsideSecret = tempDirectory.resolve("outside-secret");
        Files.write(outsideSecret, new byte[] {1, 2, 3});
        Files.createSymbolicLink(versionsDirectory.resolve("v1.key"), outsideSecret);

        DirectoryCredentialRegistry registry = new DirectoryCredentialRegistry(tempDirectory);
        assertThrows(IllegalStateException.class, () -> registry.resolve(
                new CredentialReference("tenant-claims", TENANT_PURPOSE)
        ));
    }

    private static void writeCredential(
            Path root,
            String credentialName,
            CredentialPurpose purpose,
            String version,
            byte[] secret
    ) throws IOException {
        Path credentialDirectory = Files.createDirectories(root.resolve(credentialName));
        Files.writeString(credentialDirectory.resolve("purpose"), purpose.name(), StandardCharsets.UTF_8);
        Files.writeString(credentialDirectory.resolve("active-version"), version, StandardCharsets.UTF_8);
        Path versionsDirectory = Files.createDirectories(credentialDirectory.resolve("versions"));
        Files.write(versionsDirectory.resolve(version + ".key"), secret);
    }
}
