package com.clearfolio.viewer.conversion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verified PDF result returned by an Office conversion adapter.
 *
 * <p>The result carries adapter provenance and the exact source digest supplied
 * to the converter. PDF bytes are copied at construction and on access so the
 * evidence cannot be mutated after acceptance.</p>
 *
 * @param adapterId stable adapter implementation identifier
 * @param adapterVersion qualified adapter/runtime version identifier
 * @param sourceSha256 lowercase SHA-256 digest of the source request
 * @param pdfBytes verified PDF bytes, defensively copied
 */
public record OfficeConversionResult(
        String adapterId,
        String adapterVersion,
        String sourceSha256,
        byte[] pdfBytes
) {

    /**
     * Validates result provenance and a minimal PDF media signature.
     *
     * @throws IllegalArgumentException when provenance or PDF bytes are invalid
     */
    public OfficeConversionResult {
        adapterId = requireText(adapterId, "adapterId");
        adapterVersion = requireText(adapterVersion, "adapterVersion");
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be lowercase SHA-256 hex");
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
     * Returns a defensive copy of the accepted PDF bytes.
     *
     * @return copied PDF bytes
     */
    @Override
    public byte[] pdfBytes() {
        return pdfBytes.clone();
    }

    /**
     * Returns the SHA-256 digest of the accepted PDF bytes.
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
        return value;
    }
}
