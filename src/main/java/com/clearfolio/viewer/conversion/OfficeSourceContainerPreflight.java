package com.clearfolio.viewer.conversion;

import java.util.Set;

/**
 * Performs the format-neutral source-container checks shared by qualified Office converters.
 *
 * <p>This preflight intentionally proves only two facts before untrusted bytes reach a
 * sidecar or remote converter: the declared source format belongs to the current Office
 * conversion candidate set, and the leading container signature matches that format
 * family. A matching signature is <strong>not</strong> a complete structure, macro,
 * embedded-object, archive-expansion, malware, or fidelity qualification. Those deeper
 * controls remain separate sandbox/content-policy acceptance gates.</p>
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
    private static final byte[] COMPOUND_FILE_HEADER = new byte[] {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

    private OfficeSourceContainerPreflight() {
    }

    /**
     * Rejects unknown candidate formats and obvious declared-format/container mismatches.
     *
     * @param request immutable conversion request containing declared format and source bytes
     * @throws OfficeConversionException when the format is not a current candidate or the
     *         source does not begin with that format family's required container signature
     */
    static void requireQualifiedContainer(OfficeConversionRequest request) {
        String sourceFormat = request.sourceFormat();
        byte[] sourceBytes = request.sourceBytes();

        if (ZIP_PACKAGE_FORMATS.contains(sourceFormat)) {
            requireSignature(sourceBytes, ZIP_LOCAL_FILE_HEADER);
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
        if (!startsWith(sourceBytes, expectedSignature)) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.MALFORMED_INPUT,
                    "source container signature does not match declared format"
            );
        }
    }

    private static boolean startsWith(byte[] sourceBytes, byte[] expectedSignature) {
        if (sourceBytes.length < expectedSignature.length) {
            return false;
        }
        for (int index = 0; index < expectedSignature.length; index++) {
            if (sourceBytes[index] != expectedSignature[index]) {
                return false;
            }
        }
        return true;
    }
}
