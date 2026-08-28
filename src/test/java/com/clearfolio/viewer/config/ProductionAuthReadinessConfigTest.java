package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAuthReadinessConfigTest {

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner().run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("production profile requires clearfolio.tenant-claims.hmac-secret"));
    }

    @Test
    void productionProfileFailsWithoutArtifactTokenSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=production-claims-secret")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("production profile requires clearfolio.artifact-token.secret"));
    }

    @Test
    void productionProfileStartsWithBothSigningSecrets() {
        productionRunner()
                .withPropertyValues(
                        "clearfolio.tenant-claims.hmac-secret=production-claims-secret",
                        "clearfolio.artifact-token.secret=production-artifact-secret")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
