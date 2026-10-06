package com.clearfolio.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Fails closed when production profile is started without signed tenant claims.
 */
@Configuration
@Profile("production")
public class ProductionAuthReadinessConfig {

    /**
     * Verifies that production cannot start with unsigned tenant headers.
     *
     * @param registryPort registry port for secrets
     */
    public ProductionAuthReadinessConfig(
            final CredentialRegistryPort registryPort) {
        String tenantClaimsSecret = registryPort.getCredential("clearfolio.tenant-claims.hmac-secret").orElse("");
        if (!StringUtils.hasText(tenantClaimsSecret)) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.tenant-claims.hmac-secret"
            );
        }
    }
}
