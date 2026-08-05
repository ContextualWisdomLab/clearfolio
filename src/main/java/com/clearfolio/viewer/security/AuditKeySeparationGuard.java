package com.clearfolio.viewer.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Fails application startup when policy-override key material cannot support a
 * private and attributable administrative audit trail.
 *
 * <p>The policy-override key protects an administrative authorization decision,
 * so a configured value must contain at least 32 UTF-8 bytes. Enabling that
 * signing key also requires a dedicated audit pseudonym key: accepting an
 * override while emitting only an unavailable marker would prevent operators
 * from distinguishing approvers during an investigation. The policy and audit
 * HMAC purposes remain separate security domains, and their configured values
 * must therefore be distinct.</p>
 */
@Component
public final class AuditKeySeparationGuard {

    private static final int MINIMUM_POLICY_SECRET_BYTES = 32;

    /**
     * Validates the bound conversion security configuration during bean startup.
     *
     * @param properties bound conversion configuration
     */
    public AuditKeySeparationGuard(ConversionProperties properties) {
        String policySecret = properties.getPolicyOverrideSecret();
        String auditSecret = properties.getAuditPseudonymSecret();
        requireStrongPolicySecret(policySecret);
        requireAuditKeyWhenPolicySigningIsEnabled(policySecret, auditSecret);
        requireDistinct(policySecret, auditSecret);
    }

    static void requireStrongPolicySecret(String policySecret) {
        if (!isConfigured(policySecret)) {
            return;
        }
        if (policySecret.getBytes(StandardCharsets.UTF_8).length
                < MINIMUM_POLICY_SECRET_BYTES) {
            throw new IllegalStateException(
                    "policy override key must contain at least 32 UTF-8 bytes"
            );
        }
    }

    static void requireAuditKeyWhenPolicySigningIsEnabled(
            String policySecret,
            String auditSecret
    ) {
        if (isConfigured(policySecret) && !isConfigured(auditSecret)) {
            throw new IllegalStateException(
                    "audit pseudonym key is required when policy override signing is enabled"
            );
        }
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
