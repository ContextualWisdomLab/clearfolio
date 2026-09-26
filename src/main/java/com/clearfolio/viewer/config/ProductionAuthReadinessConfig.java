package com.clearfolio.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import com.clearfolio.viewer.auth.CredentialRegistryPort;
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
     * @param credentialRegistryPort port to retrieve secrets from key vault
     */
    public ProductionAuthReadinessConfig(
            final CredentialRegistryPort credentialRegistryPort) {
        String tenantClaimsSecret = credentialRegistryPort.getCredential(CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET).orElse("");
        if (!StringUtils.hasText(tenantClaimsSecret)) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.tenant-claims.hmac-secret"
            );
        }
    }
}
