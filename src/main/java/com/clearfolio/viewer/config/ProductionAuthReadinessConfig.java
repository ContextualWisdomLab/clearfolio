package com.clearfolio.viewer.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Fails closed when production starts without stable, adequately sized, purpose-separated HMAC
 * signing material.
 */
@Configuration
@Profile("production")
public class ProductionAuthReadinessConfig {

    private static final int MINIMUM_HMAC_KEY_BYTES = 16;

    /**
     * Verifies that production cannot start with unsigned tenant headers, undersized HMAC keys,
     * an ephemeral artifact-token signing key, ambiguous tenant-key normalization, or one key reused
     * across the two signing purposes.
     *
     * <p>The tenant-claims runtime removes NUL characters and strips surrounding Unicode whitespace
     * before using the configured HMAC secret. Readiness measures that effective key so padding
     * cannot inflate the minimum length or hide purpose reuse. Production additionally rejects a
     * configured tenant secret that would be changed by that runtime normalization. This prevents an
     * operator or gateway from believing the literal configured secret is the signing key while the
     * verifier silently uses different bytes.
     *
     * <p>The 16-byte floor provides the 128-bit input-length minimum specified for HMAC
     * message-authentication keys in NIST SP 800-224's current initial public draft. Key length alone
     * does not prove entropy or approved key generation; production operators remain responsible for
     * generating and protecting both secrets with an approved secret-management boundary.
     *
     * <p>NIST SP 800-57 Part 1 Revision 5 states that, in general, one key is used for one purpose.
     * Clearfolio therefore rejects byte-identical tenant-claim and artifact-token signing keys so a
     * compromise in one authority does not automatically expose the other authority's signing key.
     * Distinct values are still not evidence of independent generation, custody, rotation, or KMS
     * provenance; those remain operational controls.
     *
     * <p>{@code ArtifactLinkService} intentionally generates a process-local random key when no
     * artifact-token secret is configured so development can run without external secret plumbing.
     * Production must instead provide stable artifact-token signing material so issued links remain
     * verifiable across process restart and replica changes.
     *
     * @param tenantClaimsSecret shared gateway signing secret
     * @param artifactTokenSecret stable artifact-token signing secret
     */
    public ProductionAuthReadinessConfig(
            @Value("${clearfolio.tenant-claims.hmac-secret:}") String tenantClaimsSecret,
            @Value("${clearfolio.artifact-token.secret:}") String artifactTokenSecret) {
        String effectiveTenantClaimsSecret = effectiveTenantClaimsSecret(tenantClaimsSecret);
        if (!StringUtils.hasText(effectiveTenantClaimsSecret)) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.tenant-claims.hmac-secret"
            );
        }
        byte[] tenantClaimsKey = effectiveTenantClaimsSecret.getBytes(StandardCharsets.UTF_8);
        if (tenantClaimsKey.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.tenant-claims.hmac-secret with at least 16 UTF-8 bytes"
            );
        }
        if (!StringUtils.hasText(artifactTokenSecret)) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.artifact-token.secret"
            );
        }
        byte[] artifactTokenKey = artifactTokenSecret.getBytes(StandardCharsets.UTF_8);
        if (artifactTokenKey.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.artifact-token.secret with at least 16 UTF-8 bytes"
            );
        }
        if (MessageDigest.isEqual(tenantClaimsKey, artifactTokenKey)) {
            throw new IllegalStateException(
                    "production profile requires distinct tenant-claims and artifact-token HMAC secrets"
            );
        }
        if (!tenantClaimsSecret.equals(effectiveTenantClaimsSecret)) {
            throw new IllegalStateException(
                    "production profile requires clearfolio.tenant-claims.hmac-secret without NUL or surrounding whitespace"
            );
        }
    }

    private static String effectiveTenantClaimsSecret(String configuredSecret) {
        if (configuredSecret == null) {
            return null;
        }
        return configuredSecret.replace("\u0000", "").strip();
    }
}
