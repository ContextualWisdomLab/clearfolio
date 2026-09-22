package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

class ProductionAuthReadinessConfigTest {

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        productionRunner().run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure().getCause().getMessage())
                    .contains("Could not resolve placeholder");
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
                .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
