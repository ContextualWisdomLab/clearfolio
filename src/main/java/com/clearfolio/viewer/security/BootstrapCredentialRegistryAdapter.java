package com.clearfolio.viewer.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Immutable in-process credential registry populated once during Spring bootstrap.
 *
 * <p>Deployment configuration is accepted only at construction time as bootstrap
 * transport. Runtime consumers resolve secrets from this registry through
 * {@link CredentialRegistryPort}; they do not bind environment-backed values
 * themselves. A future external/PostgreSQL registry can replace this adapter
 * without changing the security consumers.</p>
 */
@Component
public final class BootstrapCredentialRegistryAdapter implements CredentialRegistryPort {

    private final Map<String, String> credentials;

    /**
     * Builds the bootstrap registry from deployment-provisioned secret transport.
     *
     * @param properties conversion bootstrap properties containing the audit key
     * @param tenantClaimsHmacSecret tenant-claim verifier key bootstrap value
     */
    public BootstrapCredentialRegistryAdapter(
            final ConversionProperties properties,
            @Value("${clearfolio.tenant-claims.hmac-secret:}") final String tenantClaimsHmacSecret) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, AUDIT_PSEUDONYM_SECRET, properties.getAuditPseudonymSecret());
        putIfPresent(values, TENANT_CLAIMS_HMAC_SECRET, tenantClaimsHmacSecret);
        this.credentials = Map.copyOf(values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getCredential(final String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentials.get(name));
    }

    private static void putIfPresent(
            final Map<String, String> target,
            final String name,
            final String value) {
        String cleaned = clean(value);
        if (cleaned != null) {
            target.put(name, cleaned);
        }
    }

    private static String clean(final String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\u0000", "").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
