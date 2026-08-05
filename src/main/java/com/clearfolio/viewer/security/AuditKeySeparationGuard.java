package com.clearfolio.viewer.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Validates policy-override key material before an override-capable component
 * can accept traffic.
 *
 * <p>The policy-override key protects an administrative authorization decision,
 * so a configured value must contain at least 32 UTF-8 bytes. Enabling that
 * signing key also requires a dedicated audit pseudonym key: accepting an
 * override while emitting only an unavailable marker would prevent operators
 * from distinguishing approvers during an investigation. The policy and audit
 * HMAC purposes remain separate security domains, and their configured values
 * must therefore be distinct.</p>
 *
 * <p>Spring creates this component during application startup. Modular or
 * standalone callers that construct an override-capable service directly use
 * {@link #validate(ConversionProperties)} so they receive the same fail-closed
 * contract without depending on the Spring container.</p>
 */
@Component
public final class AuditKeySeparationGuard {

    private static final int MINIMUM_POLICY_SECRET_BYTES = 32;

    /**
     * Validates the bound conversion security configuration during bean startup.
     *
     * @param properties bound conversion configuration
     * @throws NullPointerException if {@code properties} is {@code null}
     * @throws IllegalStateException if configured key material is weak,
     *         incomplete, or reused across security purposes
     */
    public AuditKeySeparationGuard(ConversionProperties properties) {
        validate(properties);
    }

    /**
     * Applies the complete policy-override key contract for Spring-managed,
     * standalone, and modular service construction.
     *
     * <p>When policy override is disabled, both keys may be absent. When policy
     * signing is enabled, its key must contain at least 32 UTF-8 bytes, a
     * dedicated audit pseudonym key must be present, and the two values must be
     * different. The audit key performs its own strength validation when the
     * pseudonymizer is constructed.</p>
     *
     * @param properties conversion security configuration to validate
     * @throws NullPointerException if {@code properties} is {@code null}
     * @throws IllegalStateException if configured key material is weak,
     *         incomplete, or reused across security purposes
     */
    public static void validate(ConversionProperties properties) {
        ConversionProperties requiredProperties = Objects.requireNonNull(
                properties,
                "properties"
        );
        String policySecret = requiredProperties.getPolicyOverrideSecret();
        String auditSecret = requiredProperties.getAuditPseudonymSecret();
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
