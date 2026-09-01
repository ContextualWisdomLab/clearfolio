package com.clearfolio.viewer.security;

import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Keyed-HMAC adapter for retry-operator audit identities.
 *
 * <p>The adapter reuses Clearfolio's dedicated audit pseudonym key material but
 * applies a retry-operator-specific domain separator. That preserves key
 * separation from policy-override authentication and prevents the same subject
 * identifier from producing the approver-domain fingerprint used elsewhere.
 * A deployment without an audit pseudonym key receives the existing
 * non-correlatable {@code unavailable:<version>} marker rather than plaintext or
 * an unkeyed hash.</p>
 */
@Component
public final class HmacRetryOperatorIdentityAdapter implements RetryOperatorIdentityPort {

    private static final String RETRY_OPERATOR_DOMAIN = "clearfolio:audit-retry-operator:v1";

    private final AuditPseudonymizer pseudonymizer;

    /**
     * Creates the adapter from the centralized conversion security properties.
     *
     * @param properties configuration that owns the dedicated audit pseudonym
     *                   secret and non-sensitive key version
     * @throws IllegalArgumentException when configured key material violates the
     *         repository's minimum-strength or key-version contract
     */
    public HmacRetryOperatorIdentityAdapter(final ConversionProperties properties) {
        this.pseudonymizer = new AuditPseudonymizer(
                properties.getAuditPseudonymSecret(),
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
