package com.clearfolio.viewer.durable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable identity for one durable conversion acceptance contract.
 *
 * <p>The identity binds the tenant authority, exact source-byte digest,
 * conversion-policy version, and requested output-contract version. Its
 * canonical key uses length-prefixed UTF-8 fields so text containing delimiter
 * characters cannot produce ambiguous concatenations.</p>
 *
 * @param tenantId canonical tenant authority
 * @param sourceDigest canonical lowercase hexadecimal SHA-256 digest of source bytes
 * @param conversionPolicyVersion canonical conversion-policy version
 * @param outputContractVersion canonical requested output-contract version
 */
public record ConversionIdempotencyIdentity(
        String tenantId,
        String sourceDigest,
        String conversionPolicyVersion,
        String outputContractVersion) {

    private static final int MAX_AUTHORITY_LENGTH = 256;
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");

    /**
     * Validates and creates a canonical conversion idempotency identity.
     *
     * @throws NullPointerException when any identity component is null
     * @throws IllegalArgumentException when any identity component is non-canonical
     */
    public ConversionIdempotencyIdentity {
        tenantId = requireCanonicalText(tenantId, "tenantId");
        sourceDigest = requireCanonicalDigest(sourceDigest);
        conversionPolicyVersion = requireCanonicalText(
                conversionPolicyVersion,
                "conversionPolicyVersion"
        );
        outputContractVersion = requireCanonicalText(
                outputContractVersion,
                "outputContractVersion"
        );
    }

    /**
     * Returns a deterministic SHA-256 key for this complete acceptance identity.
     *
     * @return 64-character lowercase hexadecimal canonical identity key
     * @throws IllegalStateException when the required SHA-256 provider is unavailable
     */
    public String canonicalKey() {
        MessageDigest digest = sha256Digest();
        updateLengthPrefixed(digest, tenantId);
        updateLengthPrefixed(digest, sourceDigest);
        updateLengthPrefixed(digest, conversionPolicyVersion);
        updateLengthPrefixed(digest, outputContractVersion);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String requireCanonicalDigest(String value) {
        String required = Objects.requireNonNull(value, "sourceDigest");
        if (!SHA_256_HEX.matcher(required).matches()) {
            throw new IllegalArgumentException("sourceDigest must be lowercase SHA-256 hex");
        }
        return required;
    }

    private static String requireCanonicalText(String value, String name) {
        String required = Objects.requireNonNull(value, name);
        if (required.isBlank()
                || !required.equals(required.strip())
                || required.length() > MAX_AUTHORITY_LENGTH
                || CONTROL_CHARACTER.matcher(required).find()) {
            throw new IllegalArgumentException(name + " must be canonical text");
        }
        return required;
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(utf8.length).array());
        digest.update(utf8);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }
}
