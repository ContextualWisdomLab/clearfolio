package com.clearfolio.viewer.conversion;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Creates deterministic test-only Office source bytes with a qualified container signature.
 *
 * <p>The returned bytes are intentionally only sufficient for the common source-container
 * signature preflight. They are not valid complete OOXML, ODF, or compound-file documents
 * and must never be used as fidelity, archive-structure, macro, malware, or production
 * converter fixtures. Real document-fidelity qualification uses separate authorized or
 * redistributable Office fixtures.</p>
 */
final class OfficeConversionTestSource {

    private static final Set<String> ZIP_PACKAGE_FORMATS = Set.of(
            "docx", "xlsx", "pptx", "odt", "ods", "odp"
    );
    private static final Set<String> COMPOUND_FILE_FORMATS = Set.of(
            "doc", "xls", "ppt"
    );
    private static final byte[] ZIP_LOCAL_FILE_HEADER = new byte[] {
            0x50, 0x4b, 0x03, 0x04
    };
    private static final byte[] COMPOUND_FILE_HEADER = new byte[] {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

    private OfficeConversionTestSource() {
    }

    /**
     * Creates one signature-qualified source fixture for the declared candidate format.
     *
     * @param sourceFormat Office candidate format
     * @param marker deterministic marker appended after the family signature
     * @return test-only source bytes
     */
    static byte[] forFormat(String sourceFormat, String marker) {
        String normalized = sourceFormat.strip().toLowerCase(Locale.ROOT);
        byte[] payload = marker.getBytes(StandardCharsets.UTF_8);
        if (ZIP_PACKAGE_FORMATS.contains(normalized)) {
            return concatenate(ZIP_LOCAL_FILE_HEADER, payload);
        }
        if (COMPOUND_FILE_FORMATS.contains(normalized)) {
            return concatenate(COMPOUND_FILE_HEADER, payload);
        }
        return payload;
    }

    /**
     * Creates a signature-qualified ZIP-package source fixture.
     *
     * @param marker deterministic marker
     * @return test-only ZIP-family source bytes
     */
    static byte[] zipPackage(String marker) {
        return concatenate(ZIP_LOCAL_FILE_HEADER, marker.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a signature-qualified compound-file source fixture.
     *
     * @param marker deterministic marker
     * @return test-only legacy Office source bytes
     */
    static byte[] compoundFile(String marker) {
        return concatenate(COMPOUND_FILE_HEADER, marker.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] combined = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(suffix, 0, combined, prefix.length, suffix.length);
        return combined;
    }
}
