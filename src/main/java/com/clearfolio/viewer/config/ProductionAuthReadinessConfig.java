package com.clearfolio.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Fails closed when the production profile starts without signing secrets.
 */
@Configuration
@Profile("production")
public class ProductionAuthReadinessConfig {

    /**
     * Verifies that production cannot start with unsigned tenant claims or
     * process-local artifact-token signing material.
     *
     * @param tenantClaimsSecret shared gateway signing secret
     * @param artifactTokenSecret stable artifact-token signing secret
     */
    public ProductionAuthReadinessConfig(
            @Value("${clearfolio.tenant-claims.hmac-secret}")
            final String tenantClaimsSecret,
            @Value("${clearfolio.artifact-token.secret}")
            final String artifactTokenSecret) {
        if (!StringUtils.hasText(tenantClaimsSecret)) {
            throw new IllegalStateException(
                    "production profile requires "
                            + "clearfolio.tenant-claims.hmac-secret"
            );
        }
        if (!StringUtils.hasText(artifactTokenSecret)) {
            throw new IllegalStateException(
                    "production profile requires "
                            + "clearfolio.artifact-token.secret"
            );
        }
    }
}
