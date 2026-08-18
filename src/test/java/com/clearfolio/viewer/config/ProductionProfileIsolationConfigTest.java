package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionProfileIsolationConfigTest {

    @Test
    void productionProfileRejectsBuyerDemoProfile() {
        runnerWithProfiles("production", "buyer-demo")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("production profile cannot run with buyer-demo profile"));
    }

    @Test
    void productionProfileStartsWithoutBuyerDemoProfile() {
        runnerWithProfiles("production")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void buyerDemoProfileDoesNotActivateProductionGuard() {
        runnerWithProfiles("buyer-demo")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).doesNotHaveBean(ProductionProfileIsolationConfig.class);
                });
    }

    private static ApplicationContextRunner runnerWithProfiles(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionProfileIsolationConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles));
    }
}
