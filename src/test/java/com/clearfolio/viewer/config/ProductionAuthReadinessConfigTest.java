package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAuthReadinessConfigTest {

    private static final String TENANT_SECRET_32_BYTES = "t".repeat(32);
    private static final String ARTIFACT_SECRET_32_BYTES = "a".repeat(32);
    private static final String STABLE_ARTIFACT_TOKEN_SECRET =
            "clearfolio.artifact-token.secret=" + ARTIFACT_SECRET_32_BYTES;
    private static final String MINIMUM_UTF8_TENANT_SECRET = "é".repeat(16);
    private static final String MINIMUM_UTF8_ARTIFACT_SECRET = "界".repeat(10) + "aa";

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner().run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("production profile requires clearfolio.tenant-claims.hmac-secret"));
    }

    @Test
    void productionReadinessFailsWhenTenantSecretIsExplicitlyNull() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        null,
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.tenant-claims.hmac-secret");
    }

    @Test
    void productionProfileRejectsPreviouslyAcceptedSixteenByteTenantSecret() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=0123456789abcdef",
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires clearfolio.tenant-claims.hmac-secret with at least 32 UTF-8 bytes"
                        ));
    }

    @Test
    void productionReadinessMeasuresEffectiveTenantClaimsKeyAfterSanitization() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        "                               a",
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.tenant-claims.hmac-secret with at least 32 UTF-8 bytes");
    }

    @Test
    void productionReadinessRejectsTenantSecretChangedByRuntimeSanitization() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        " " + TENANT_SECRET_32_BYTES + " ",
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.tenant-claims.hmac-secret without NUL or surrounding whitespace"
                );
    }

    @Test
    void productionReadinessRejectsTenantSecretContainingNul() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        TENANT_SECRET_32_BYTES + "\u0000q",
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.tenant-claims.hmac-secret without NUL or surrounding whitespace"
                );
    }

    @Test
    void productionReadinessRejectsWhitespaceOnlyTenantSecret() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        "                                ",
                        ARTIFACT_SECRET_32_BYTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.tenant-claims.hmac-secret");
    }

    @Test
    void productionProfileFailsWithoutStableArtifactTokenSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=" + TENANT_SECRET_32_BYTES)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("production profile requires clearfolio.artifact-token.secret"));
    }

    @Test
    void productionReadinessRejectsWhitespaceOnlyArtifactTokenSecret() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        TENANT_SECRET_32_BYTES,
                        "                                "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.artifact-token.secret");
    }

    @Test
    void productionProfileRejectsPreviouslyAcceptedSixteenByteArtifactSecret() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=" + TENANT_SECRET_32_BYTES,
                        "clearfolio.artifact-token.secret=0123456789abcdef")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires clearfolio.artifact-token.secret with at least 32 UTF-8 bytes"
                        ));
    }

    @Test
    void productionProfileFailsWhenTenantAndArtifactSigningKeysAreReused() {
        String sharedSecret = "s".repeat(32);
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=" + sharedSecret,
                        "clearfolio.artifact-token.secret=" + sharedSecret)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires distinct tenant-claims and artifact-token HMAC secrets"
                        ));
    }

    @Test
    void productionReadinessRejectsPurposeReuseAfterTenantNormalization() {
        String sharedSecret = "s".repeat(32);
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        " " + sharedSecret + " ",
                        sharedSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires distinct tenant-claims and artifact-token HMAC secrets");
    }

    @Test
    void productionProfileStartsAtMinimumSignedTenantClaimsSecretLength() {
        assertThat(MINIMUM_UTF8_TENANT_SECRET.getBytes(StandardCharsets.UTF_8)).hasSize(32);
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=" + MINIMUM_UTF8_TENANT_SECRET,
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void productionProfileStartsAtMinimumArtifactTokenSecretLength() {
        assertThat(MINIMUM_UTF8_ARTIFACT_SECRET.getBytes(StandardCharsets.UTF_8)).hasSize(32);
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=" + TENANT_SECRET_32_BYTES,
                        "clearfolio.artifact-token.secret=" + MINIMUM_UTF8_ARTIFACT_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void productionProfileStartsWithDistinctThirtyTwoByteSigningSecrets() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=" + TENANT_SECRET_32_BYTES,
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
