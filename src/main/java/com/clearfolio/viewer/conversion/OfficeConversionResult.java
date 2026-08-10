package com.clearfolio.viewer.conversion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Candidate PDF result returned by an Office conversion provider.
 *
 * <p>The result carries adapter provenance, source provenance, and—when supplied
 * by the provider—the complete immutable request binding. PDF bytes are copied
 * at construction and on access. A result is not trusted merely because this
 * record can be constructed: {@link OfficeConversionAdapter#convert} is the
 * authority that verifies source and full request binding before acceptance.</p>
 *
 * @param adapterId stable adapter implementation identifier
 * @param adapterVersion qualified adapter/runtime version identifier
 * @param sourceSha256 lowercase SHA-256 digest of the source request
 * @param requestBinding complete request authority tuple supplied by the provider,
 *        or {@code null} for an unbound candidate that the adapter must reject
 * @param pdfBytes candidate PDF bytes, defensively copied
 */
public record OfficeConversionResult(
        String adapterId,
        String adapterVersion,
        String sourceSha256,
        OfficeConversionRequestBinding requestBinding,
        byte[] pdfBytes
) {

    /**
     * Creates a source-only candidate result for low-level result validation.
     *
     * <p>Provider implementations should normally supply the full request binding.
     * A source-only result is intentionally rejected by the public adapter
     * acceptance boundary.</p>
     *
     * @param adapterId stable adapter implementation identifier
     * @param adapterVersion qualified adapter/runtime version identifier
     * @param sourceSha256 lowercase SHA-256 source digest
     * @param pdfBytes candidate PDF bytes
     */
    public OfficeConversionResult(
            String adapterId,
            String adapterVersion,
            String sourceSha256,
            byte[] pdfBytes) {
        this(adapterId, adapterVersion, sourceSha256, null, pdfBytes);
    }

    /**
     * Validates candidate provenance and a minimal PDF media signature.
     *
     * @throws IllegalArgumentException when provenance or PDF bytes are invalid
     */
    public OfficeConversionResult {
        adapterId = requireText(adapterId, "adapterId");
        adapterVersion = requireText(adapterVersion, "adapterVersion");
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be lowercase SHA-256 hex");
        }
        if (requestBinding != null && !sourceSha256.equals(requestBinding.sourceSha256())) {
            throw new IllegalArgumentException("request binding source digest mismatch");
        }
        if (pdfBytes == null) {
            throw new IllegalArgumentException("pdfBytes must not be null");
        }
        pdfBytes = pdfBytes.clone();
        String prefix = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5), StandardCharsets.US_ASCII);
        if (!"%PDF-".equals(prefix)) {
            throw new IllegalArgumentException("converter output is not a PDF");
        }
    }

    /**
     * Returns a defensive copy of the candidate PDF bytes.
     *
     * @return copied PDF bytes
     */
    @Override
    public byte[] pdfBytes() {
        return pdfBytes.clone();
    }

    /**
     * Returns the SHA-256 digest of the candidate PDF bytes.
     *
     * @return lowercase hexadecimal SHA-256 digest
     */
    public String outputSha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdfBytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
