package com.clearfolio.viewer.conversion;

import java.util.Set;

/**
 * Performs format-neutral source-container checks shared by qualified Office converters.
 *
 * <p>This preflight intentionally proves only a bounded set of facts before untrusted
 * bytes reach a sidecar or remote converter: the declared source format belongs to the
 * current Office conversion candidate set, the leading container signature matches that
 * format family, and ZIP-family candidates contain a self-consistent standard single-disk
 * central-directory/end-of-central-directory frame. Passing this preflight is
 * <strong>not</strong> complete package, macro, embedded-object, archive-expansion,
 * malware, or fidelity qualification. Those deeper controls remain separate
 * sandbox/content-policy acceptance gates.</p>
 */
final class OfficeSourceContainerPreflight {

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
    private static final int ZIP_EOCD_MINIMUM_LENGTH = 22;
    private static final int ZIP_MAXIMUM_COMMENT_LENGTH = 65_535;
    private static final int ZIP16_SENTINEL = 0xffff;
    private static final long ZIP32_SENTINEL = 0xffff_ffffL;

    private OfficeSourceContainerPreflight() {
    }

    /**
     * Rejects unknown candidate formats and obvious declared-format/container mismatches.
     *
     * @param request immutable conversion request containing declared format and source bytes
     * @throws OfficeConversionException when the format is not a current candidate, the
     *         source does not match that format family's required container signature, or a
     *         ZIP-family source lacks bounded standard single-disk central-directory framing
     */
    static void requireQualifiedContainer(OfficeConversionRequest request) {
        String sourceFormat = request.sourceFormat();
        byte[] sourceBytes = request.sourceBytes();

        if (ZIP_PACKAGE_FORMATS.contains(sourceFormat)) {
            requireSignature(sourceBytes, ZIP_LOCAL_FILE_HEADER);
            requireStandardZipFraming(sourceBytes);
            return;
        }
        if (COMPOUND_FILE_FORMATS.contains(sourceFormat)) {
            requireSignature(sourceBytes, COMPOUND_FILE_HEADER);
            return;
        }
        throw new OfficeConversionException(
                OfficeConversionFailureCode.UNSUPPORTED_FORMAT,
                "source format is not an Office conversion candidate"
        );
    }

    private static void requireSignature(byte[] sourceBytes, byte[] expectedSignature) {
        if (!matchesAt(sourceBytes, 0, expectedSignature)) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.MALFORMED_INPUT,
                    "source container signature does not match declared format"
            );
        }
    }

    private static void requireStandardZipFraming(byte[] sourceBytes) {
        int eocdOffset = findEocdOffset(sourceBytes);
        if (eocdOffset < 0 || !isStandardSingleDiskEocd(sourceBytes, eocdOffset)) {
            throw invalidZipFraming();
        }

        int entryCount = unsignedShort(sourceBytes, eocdOffset + 10);
        long centralDirectorySize = unsignedInt(sourceBytes, eocdOffset + 12);
        long centralDirectoryOffset = unsignedInt(sourceBytes, eocdOffset + 16);
        if (entryCount == ZIP16_SENTINEL
                || centralDirectorySize == ZIP32_SENTINEL
                || centralDirectoryOffset == ZIP32_SENTINEL
                || centralDirectorySize == 0L
                || centralDirectoryOffset > Integer.MAX_VALUE) {
            throw invalidZipFraming();
        }

        long centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize;
        if (centralDirectoryEnd > eocdOffset
                || !matchesAt(sourceBytes, (int) centralDirectoryOffset, ZIP_CENTRAL_DIRECTORY_HEADER)) {
            throw invalidZipFraming();
        }
    }

    private static int findEocdOffset(byte[] sourceBytes) {
        if (sourceBytes.length < ZIP_EOCD_MINIMUM_LENGTH) {
            return -1;
        }
        int latest = sourceBytes.length - ZIP_EOCD_MINIMUM_LENGTH;
        int earliest = Math.max(0, latest - ZIP_MAXIMUM_COMMENT_LENGTH);
        for (int offset = latest; offset >= earliest; offset--) {
            if (!matchesAt(sourceBytes, offset, ZIP_END_OF_CENTRAL_DIRECTORY)) {
                continue;
            }
            int commentLength = unsignedShort(sourceBytes, offset + 20);
            if (offset + ZIP_EOCD_MINIMUM_LENGTH + commentLength == sourceBytes.length) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean isStandardSingleDiskEocd(byte[] sourceBytes, int eocdOffset) {
        int diskNumber = unsignedShort(sourceBytes, eocdOffset + 4);
        int centralDirectoryDisk = unsignedShort(sourceBytes, eocdOffset + 6);
        int entriesOnDisk = unsignedShort(sourceBytes, eocdOffset + 8);
        int totalEntries = unsignedShort(sourceBytes, eocdOffset + 10);
        return diskNumber == 0
                && centralDirectoryDisk == 0
                && entriesOnDisk > 0
                && entriesOnDisk == totalEntries;
    }

    private static boolean matchesAt(byte[] sourceBytes, int offset, byte[] signature) {
        if (offset > sourceBytes.length - signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (sourceBytes[offset + index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static int unsignedShort(byte[] sourceBytes, int offset) {
        return Byte.toUnsignedInt(sourceBytes[offset])
                | (Byte.toUnsignedInt(sourceBytes[offset + 1]) << 8);
    }

    private static long unsignedInt(byte[] sourceBytes, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(sourceBytes[offset])
                        | (Byte.toUnsignedInt(sourceBytes[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(sourceBytes[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(sourceBytes[offset + 3]) << 24)
        );
    }

    private static OfficeConversionException invalidZipFraming() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ZIP container framing is invalid"
        );
    }
}
