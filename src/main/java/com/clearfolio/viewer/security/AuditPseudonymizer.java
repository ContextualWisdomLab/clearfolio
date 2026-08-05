package com.clearfolio.viewer.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Produces privacy-safe, domain-separated audit fingerprints for identifiers.
 *
 * <p>The pseudonymizer deliberately uses a dedicated keyed HMAC rather than an
 * unkeyed digest. This prevents practical dictionary attacks against common
 * low-entropy identifiers such as usernames, employee numbers, and email
 * addresses. Fingerprints are stable only within the configured key version
 * and domain.</p>
 */
public final class AuditPseudonymizer {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String DEFAULT_KEY_VERSION = "v1";
    private static final String APPROVER_DOMAIN = "clearfolio:audit-approver:v1";
    private static final int FINGERPRINT_BYTES = 16;
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_KEY_VERSION_LENGTH = 32;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final byte[] secretBytes;
    private final String keyVersion;
    private final String domain;

    /**
     * Creates an approver audit pseudonymizer.
     *
     * @param secret dedicated audit pseudonym secret; when configured, it must
     *               contain at least 32 UTF-8 bytes; blank disables correlation
     * @param keyVersion non-sensitive key-rotation identifier
     */
    public AuditPseudonymizer(String secret, String keyVersion) {
        this(secret, keyVersion, APPROVER_DOMAIN);
    }

    /**
     * Creates a pseudonymizer with an explicit domain for isolated internal use
     * and domain-separation verification.
     *
     * @param secret dedicated audit pseudonym secret; when configured, it must
     *               contain at least 32 UTF-8 bytes; blank disables correlation
     * @param keyVersion non-sensitive key-rotation identifier
     * @param domain stable protocol-specific domain separator
     */
    AuditPseudonymizer(String secret, String keyVersion, String domain) {
        this.secretBytes = configuredSecretBytes(secret);
        this.keyVersion = normalizeKeyVersion(keyVersion);
        this.domain = requireDomain(domain);
    }

    /**
     * Returns a stable keyed fingerprint without exposing the supplied identifier.
     *
     * <p>Null input is represented by a fixed absent marker. If no dedicated key
     * is configured, the method emits a fixed non-correlatable unavailable marker
     * instead of falling back to plaintext or an unkeyed hash. Empty input remains
     * distinct from absent input and is HMACed exactly as supplied.</p>
     *
     * @param identifier exact identifier bytes represented as a Java string
     * @return versioned fingerprint or a fixed safe marker
     */
    public String fingerprint(String identifier) {
        if (identifier == null) {
            return "absent:" + keyVersion;
        }
        if (secretBytes == null) {
            return "unavailable:" + keyVersion;
        }

        String payload = domain + "\n" + identifier;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA_256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return keyVersion + ":" + HEX_FORMAT.formatHex(digest, 0, FINGERPRINT_BYTES);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("audit pseudonym HMAC unavailable", ex);
        }
    }

    private static byte[] configuredSecretBytes(String secret) {
        String configuredSecret = cleanSecret(secret);
        if (configuredSecret == null) {
            return null;
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "audit pseudonym secret must contain at least 32 UTF-8 bytes"
            );
        }
        return bytes;
    }

    private static String cleanSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return secret;
    }

    private static String normalizeKeyVersion(String keyVersion) {
        if (keyVersion == null) {
            return DEFAULT_KEY_VERSION;
        }
        String normalized = keyVersion.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("audit pseudonym key version must not be blank");
        }
        if (normalized.length() > MAX_KEY_VERSION_LENGTH) {
            throw new IllegalArgumentException("audit pseudonym key version is too long");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            boolean safe = Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '_'
                    || character == '-';
            if (!safe) {
                throw new IllegalArgumentException("audit pseudonym key version contains unsafe characters");
            }
        }
        return normalized;
    }

    private static String requireDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("audit pseudonym domain is required");
        }
        return domain;
    }
}
