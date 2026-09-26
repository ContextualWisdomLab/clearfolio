package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAuthReadinessConfigTest {

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("Error creating bean with name 'productionAuthReadinessConfig'");
            assertThat(context.getStartupFailure().getCause().getMessage()).contains("Could not resolve placeholder");
        });
    }

    @Test
    void productionProfileStartsWithSignedTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=production-secret")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }


    @Test
    void productionProfileFailsWithEmptyTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("production profile requires clearfolio.tenant-claims.hmac-secret"));
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionAuthReadinessConfig.class, org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
