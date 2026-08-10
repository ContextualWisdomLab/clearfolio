package com.clearfolio.viewer.conversion;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Creates deterministic test-only Office source bytes for conversion-boundary tests.
 *
 * <p>ZIP-family fixtures contain only enough framing to satisfy the common source
 * preflight: a local-file signature, deterministic marker bytes, one central-directory
 * record with a safe relative entry name, and a self-consistent standard single-disk
 * end-of-central-directory record. Legacy fixtures contain only the compound-file family
 * signature plus marker bytes. These are <strong>not</strong> valid complete OOXML, ODF,
 * or compound-file documents and must never be used as fidelity, archive-structure,
 * macro, malware, or production converter fixtures. Real document-fidelity qualification
 * uses separate authorized or redistributable Office fixtures.</p>
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
    private static final byte[] ZIP_CENTRAL_DIRECTORY_HEADER = new byte[] {
            0x50, 0x4b, 0x01, 0x02
    };
    private static final byte[] ZIP_END_OF_CENTRAL_DIRECTORY = new byte[] {
            0x50, 0x4b, 0x05, 0x06
    };
    private static final byte[] COMPOUND_FILE_HEADER = new byte[] {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };
    private static final byte[] ZIP_SAFE_ENTRY_NAME = "content.xml".getBytes(StandardCharsets.UTF_8);
    private static final int ZIP_CENTRAL_DIRECTORY_FIXED_LENGTH = 46;
    private static final int ZIP_EOCD_LENGTH = 22;

    private OfficeConversionTestSource() {
    }

    /**
     * Creates one preflight-qualified source fixture for the declared candidate format.
     *
     * @param sourceFormat Office candidate format
     * @param marker deterministic marker kept inside the test container framing
     * @return test-only source bytes
     */
    static byte[] forFormat(String sourceFormat, String marker) {
        String normalized = sourceFormat.strip().toLowerCase(Locale.ROOT);
        if (ZIP_PACKAGE_FORMATS.contains(normalized)) {
            return zipPackage(marker);
        }
        if (COMPOUND_FILE_FORMATS.contains(normalized)) {
            return compoundFile(marker);
        }
        return marker.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a bounded-framing ZIP-package source fixture.
     *
     * @param marker deterministic marker
     * @return test-only ZIP-family source bytes
     */
    static byte[] zipPackage(String marker) {
        byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
        int centralDirectoryOffset = ZIP_LOCAL_FILE_HEADER.length + markerBytes.length;
        int centralDirectoryLength = ZIP_CENTRAL_DIRECTORY_FIXED_LENGTH + ZIP_SAFE_ENTRY_NAME.length;
        int eocdOffset = centralDirectoryOffset + centralDirectoryLength;
        byte[] bytes = new byte[eocdOffset + ZIP_EOCD_LENGTH];

        System.arraycopy(ZIP_LOCAL_FILE_HEADER, 0, bytes, 0, ZIP_LOCAL_FILE_HEADER.length);
        System.arraycopy(markerBytes, 0, bytes, ZIP_LOCAL_FILE_HEADER.length, markerBytes.length);
        System.arraycopy(
                ZIP_CENTRAL_DIRECTORY_HEADER,
                0,
                bytes,
                centralDirectoryOffset,
                ZIP_CENTRAL_DIRECTORY_HEADER.length
        );
        putUnsignedShort(bytes, centralDirectoryOffset + 28, ZIP_SAFE_ENTRY_NAME.length);
        putUnsignedInt(bytes, centralDirectoryOffset + 42, 0L);
        System.arraycopy(
                ZIP_SAFE_ENTRY_NAME,
                0,
                bytes,
                centralDirectoryOffset + ZIP_CENTRAL_DIRECTORY_FIXED_LENGTH,
                ZIP_SAFE_ENTRY_NAME.length
        );
        System.arraycopy(
                ZIP_END_OF_CENTRAL_DIRECTORY,
                0,
                bytes,
                eocdOffset,
                ZIP_END_OF_CENTRAL_DIRECTORY.length
        );
        putUnsignedShort(bytes, eocdOffset + 8, 1);
        putUnsignedShort(bytes, eocdOffset + 10, 1);
        putUnsignedInt(bytes, eocdOffset + 12, centralDirectoryLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralDirectoryOffset);
        putUnsignedShort(bytes, eocdOffset + 20, 0);
        return bytes;
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

    private static void putUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void putUnsignedInt(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    private static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] combined = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(suffix, 0, combined, prefix.length, suffix.length);
        return combined;
    }
}
