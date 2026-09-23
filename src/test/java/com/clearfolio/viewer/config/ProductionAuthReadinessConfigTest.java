package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

class ProductionAuthReadinessConfigTest {

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner()
                .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
                .run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure().getCause().getMessage())
                    .contains("Could not resolve placeholder");
        });
    }

    @Test
    void productionProfileFailsWithEmptySignedTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure().getCause().getCause().getMessage())
                            .contains("production profile requires clearfolio.tenant-claims.hmac-secret");
                });
    }

    @Test
    void productionProfileStartsWithSignedTenantClaimsSecret() {
        productionRunner()
                .withPropertyValues("clearfolio.tenant-claims.hmac-secret=production-secret")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
