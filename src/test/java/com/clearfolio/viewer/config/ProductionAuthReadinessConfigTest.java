package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.clearfolio.viewer.auth.CredentialRegistryPort;

class ProductionAuthReadinessConfigTest {

    @Test
    void productionProfileFailsWithoutSignedTenantClaimsSecret() {
        new ApplicationContextRunner()
                .withBean(CredentialRegistryPort.class, () -> (CredentialRegistryPort) (final String name) -> java.util.Optional.empty())
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage("production profile requires clearfolio.tenant-claims.hmac-secret"));
    }

    @Test
    void productionProfileStartsWithSignedTenantClaimsSecret() {
        new ApplicationContextRunner()
                .withBean(CredentialRegistryPort.class, () -> (CredentialRegistryPort) (final String name) -> java.util.Optional.of("production-secret"))
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private static ApplicationContextRunner productionRunner() {
        return new ApplicationContextRunner()
                .withBean(CredentialRegistryPort.class, () -> (CredentialRegistryPort) (final String name) -> java.util.Optional.empty())
                .withUserConfiguration(ProductionAuthReadinessConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));
    }
}
