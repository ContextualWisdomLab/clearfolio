package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.CredentialSnapshot;

class ProductionAuthReadinessConfigTest {

    private static final byte[] TENANT_SECRET_32_BYTES = "t".repeat(32)
            .getBytes(StandardCharsets.UTF_8);
    private static final String ARTIFACT_SECRET_32_BYTES = "a".repeat(32);

    @Test
    void productionReadinessRejectsMissingRegistry() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        null,
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("credentialRegistry");
    }

    @Test
    void productionReadinessRejectsWrongTenantCredentialIdentity() {
        CredentialRegistry registry = registry(
                "different-credential",
                CredentialPurpose.TENANT_CLAIMS_SIGNING,
                TENANT_SECRET_32_BYTES
        );

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        registry,
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production tenant-claims credential identity mismatch");
    }

    @Test
    void productionReadinessRejectsWrongTenantCredentialPurpose() {
        CredentialRegistry registry = registry(
                "tenant-claims-signing",
                CredentialPurpose.ARTIFACT_TOKEN_SIGNING,
                TENANT_SECRET_32_BYTES
        );

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        registry,
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credential registry returned material for a different purpose");
    }

    @Test
    void productionReadinessRejectsUndersizedTenantCredential() {
        CredentialRegistry registry = tenantRegistry("t".repeat(31).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        registry,
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production tenant-claims credential requires at least 32 bytes");
    }

    @Test
    void productionReadinessRejectsMissingOrBlankArtifactTokenSecret() {
        CredentialRegistry registry = tenantRegistry(TENANT_SECRET_32_BYTES);

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(registry, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.artifact-token.secret");
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(registry, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.artifact-token.secret");
    }

    @Test
    void productionReadinessRejectsUndersizedArtifactTokenSecret() {
        CredentialRegistry registry = tenantRegistry(TENANT_SECRET_32_BYTES);

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        registry,
                        "a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.artifact-token.secret with at least 32 UTF-8 bytes"
                );
    }

    @Test
    void productionReadinessRejectsPurposeReuse() {
        String sharedSecret = "s".repeat(32);
        CredentialRegistry registry = tenantRegistry(sharedSecret.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(registry, sharedSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires distinct tenant-claims and artifact-token HMAC secrets"
                );
    }

    @Test
    void productionReadinessAcceptsDistinctMinimumLengthAuthorities() {
        byte[] tenantSecret = "é".repeat(16).getBytes(StandardCharsets.UTF_8);
        String artifactSecret = "界".repeat(10) + "aa";
        CredentialRegistry registry = tenantRegistry(tenantSecret);

        assertThatCode(() -> new ProductionAuthReadinessConfig(registry, artifactSecret))
                .doesNotThrowAnyException();
    }

    private static CredentialRegistry tenantRegistry(byte[] secretBytes) {
        return registry(
                "tenant-claims-signing",
                CredentialPurpose.TENANT_CLAIMS_SIGNING,
                secretBytes
        );
    }

    private static CredentialRegistry registry(
            String credentialId,
            CredentialPurpose purpose,
            byte[] secretBytes
    ) {
        return reference -> new CredentialSnapshot(
                credentialId,
                "v1",
                purpose,
                secretBytes
        );
    }
}
