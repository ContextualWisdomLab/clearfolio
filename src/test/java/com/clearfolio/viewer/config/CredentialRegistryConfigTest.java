package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialReference;
import com.clearfolio.viewer.credential.CredentialRegistry;

class CredentialRegistryConfigTest {

    @TempDir
    Path tempDirectory;

    @Test
    void productionRegistryConfigOpensMountedDirectory() throws IOException {
        writeTenantCredential(tempDirectory, "v1", "t".repeat(32));

        CredentialRegistry registry = new CredentialRegistryConfig()
                .credentialRegistry(tempDirectory.toString());

        assertThat(registry.resolveScoped(new CredentialReference(
                "tenant-claims-signing",
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        )).secretBytes()).hasSize(32);
    }

    @Test
    void productionRegistryConfigRejectsMissingOrBlankRoot() {
        CredentialRegistryConfig config = new CredentialRegistryConfig();

        assertThatThrownBy(() -> config.credentialRegistry(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.credential-registry.root-directory"
                );
        assertThatThrownBy(() -> config.credentialRegistry(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.credential-registry.root-directory"
                );
    }

    @Test
    void productionRegistryConfigRejectsNonDirectoryRoot() throws IOException {
        Path regularFile = tempDirectory.resolve("not-a-directory");
        Files.writeString(regularFile, "x", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new CredentialRegistryConfig()
                        .credentialRegistry(regularFile.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production credential registry unavailable")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    static void writeTenantCredential(Path root, String version, String secret) throws IOException {
        Path credentialDirectory = Files.createDirectories(root.resolve("tenant-claims-signing"));
        Files.writeString(
                credentialDirectory.resolve("purpose"),
                CredentialPurpose.TENANT_CLAIMS_SIGNING.name(),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                credentialDirectory.resolve("active-version"),
                version,
                StandardCharsets.UTF_8
        );
        Path versionsDirectory = Files.createDirectories(credentialDirectory.resolve("versions"));
        Files.writeString(
                versionsDirectory.resolve(version + ".key"),
                secret,
                StandardCharsets.UTF_8
        );
    }
}
