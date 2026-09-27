package com.clearfolio.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails closed when production profile is started without signed tenant claims.
 */
@Configuration
@Profile("production")
public class ProductionAuthReadinessConfig {

    /**
     * Verifies that production cannot start with unsigned tenant headers.
     *
     * @param tenantClaimsSecret shared gateway signing secret
     */
    public ProductionAuthReadinessConfig(
            @Value("${clearfolio.tenant-claims.hmac-secret}") String tenantClaimsSecret) {
        // Spring fails fast before instantiation if the secret is unmounted because there is no default value.
    }
}
