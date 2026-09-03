package com.clearfolio.viewer.security;

import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Keyed-HMAC adapter for retry-operator audit identities.
 *
 * <p>The adapter resolves the dedicated audit pseudonym key through the
 * credential-registry port and applies a retry-operator-specific domain
 * separator. That preserves key separation from policy-override authentication
 * and prevents the same subject identifier from producing the approver-domain
 * fingerprint used elsewhere. A registry without the audit pseudonym key
 * yields the existing non-correlatable {@code unavailable:<version>} marker
 * rather than plaintext or an unkeyed hash.</p>
 */
@Component
public final class HmacRetryOperatorIdentityAdapter implements RetryOperatorIdentityPort {

    private static final String RETRY_OPERATOR_DOMAIN = "clearfolio:audit-retry-operator:v1";

    private final AuditPseudonymizer pseudonymizer;

    /**
     * Creates the adapter from the runtime credential registry and non-secret
     * audit key version configuration.
     *
     * @param credentialRegistry runtime credential registry
     * @param properties configuration containing only the non-sensitive key version
     * @throws IllegalArgumentException when configured key material violates the
     *         repository's minimum-strength or key-version contract
     */
    public HmacRetryOperatorIdentityAdapter(
            final CredentialRegistryPort credentialRegistry,
            final ConversionProperties properties) {
        String auditSecret = credentialRegistry
                .getCredential(CredentialRegistryPort.AUDIT_PSEUDONYM_SECRET)
                .orElse("");
        this.pseudonymizer = new AuditPseudonymizer(
                auditSecret,
                properties.getAuditPseudonymKeyVersion(),
                RETRY_OPERATOR_DOMAIN
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String pseudonymize(final String subjectId) {
        return pseudonymizer.fingerprint(subjectId);
    }
}
