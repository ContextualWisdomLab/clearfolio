package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAuthReadinessConfigTest {

    private static final String STABLE_ARTIFACT_TOKEN_SECRET =
            "clearfolio.artifact-token.secret=stable-artifact-token-secret";

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner().run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("production profile requires clearfolio.tenant-claims.hmac-secret"));
    }

    @Test
    void productionReadinessFailsClosedForNullTenantClaimsSecret() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        null,
                        "stable-artifact-token-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.tenant-claims.hmac-secret");
    }

    @Test
    void productionProfileFailsWithShortSignedTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=short-hmac-key!",
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires clearfolio.tenant-claims.hmac-secret with at least 16 UTF-8 bytes"
                        ));
    }

    @Test
    void productionReadinessMeasuresEffectiveTenantClaimsKeyAfterSanitization() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        "               a",
                        "stable-artifact-token-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires clearfolio.tenant-claims.hmac-secret with at least 16 UTF-8 bytes");
    }

    @Test
    void productionReadinessRejectsTenantSecretChangedByRuntimeSanitization() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        " strong-tenant-signing-key ",
                        "stable-artifact-token-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "production profile requires clearfolio.tenant-claims.hmac-secret without NUL or surrounding whitespace"
                );
    }

    @Test
    void productionProfileFailsWithoutStableArtifactTokenSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=0123456789abcdef")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("production profile requires clearfolio.artifact-token.secret"));
    }

    @Test
    void productionProfileFailsWithShortArtifactTokenSecret() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=0123456789abcdef",
                        "clearfolio.artifact-token.secret=short-art-key!")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires clearfolio.artifact-token.secret with at least 16 UTF-8 bytes"
                        ));
    }

    @Test
    void productionProfileFailsWhenTenantAndArtifactSigningKeysAreReused() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=shared-signing-secret",
                        "clearfolio.artifact-token.secret=shared-signing-secret")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "production profile requires distinct tenant-claims and artifact-token HMAC secrets"
                        ));
    }

    @Test
    void productionReadinessRejectsPurposeReuseAfterTenantNormalization() {
        assertThatThrownBy(() -> new ProductionAuthReadinessConfig(
                        " shared-signing-secret ",
                        "shared-signing-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production profile requires distinct tenant-claims and artifact-token HMAC secrets");
    }

    @Test
    void productionProfileStartsAtMinimumSignedTenantClaimsSecretLength() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=0123456789abcdef",
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void productionProfileStartsAtMinimumArtifactTokenSecretLength() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=production-secret",
                        "clearfolio.artifact-token.secret=0123456789abcdef")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void productionProfileStartsWithSignedTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=production-secret",
                        STABLE_ARTIFACT_TOKEN_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
