package com.clearfolio.viewer.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Fails application startup when policy signing and audit pseudonymization use
 * the same configured key material.
 *
 * <p>The two HMAC purposes form separate security domains. Reusing one value
 * would allow a holder of the audit key to create policy-override signatures,
 * so nonblank configured values must remain distinct.</p>
 */
@Component
public final class AuditKeySeparationGuard {

    /**
     * Validates the bound conversion security configuration during bean startup.
     *
     * @param properties bound conversion configuration
     */
    public AuditKeySeparationGuard(ConversionProperties properties) {
        requireDistinct(
                properties.getPolicyOverrideSecret(),
                properties.getAuditPseudonymSecret()
        );
    }

    static void requireDistinct(String policySecret, String auditSecret) {
        if (!isConfigured(policySecret) || !isConfigured(auditSecret)) {
            return;
        }
        if (MessageDigest.isEqual(
                policySecret.getBytes(StandardCharsets.UTF_8),
                auditSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                    "policy override and audit pseudonym keys must be different"
            );
        }
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
