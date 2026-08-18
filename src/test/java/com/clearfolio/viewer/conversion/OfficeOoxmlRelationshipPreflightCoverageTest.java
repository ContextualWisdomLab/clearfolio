package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for malformed OOXML relationship container metadata.
 */
class OfficeOoxmlRelationshipPreflightCoverageTest {

    private static final String RELATIONSHIP_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    @Test
    void rejectsDeclaredRelationshipExpansionMismatch() throws Exception {
        byte[] source = relationshipZip();
        int centralDirectoryOffset = centralDirectoryOffset(source);
        long declaredSize = unsignedInt(source, centralDirectoryOffset + 24);
        putUnsignedInt(source, centralDirectoryOffset + 24, declaredSize + 1);

        assertMalformed(source);
    }

    @Test
    void rejectsCorruptedRelationshipDeflateStream() throws Exception {
        byte[] source = relationshipZip();
        int centralDirectoryOffset = centralDirectoryOffset(source);
        int localHeaderOffset = (int) unsignedInt(source, centralDirectoryOffset + 42);
        int localNameLength = unsignedShort(source, localHeaderOffset + 26);
        int localExtraLength = unsignedShort(source, localHeaderOffset + 28);
        int dataOffset = localHeaderOffset + 30 + localNameLength + localExtraLength;

        source[dataOffset] = (byte) ((source[dataOffset] & 0xF9) | 0x06);

        assertMalformed(source);
    }

    @Test
    void rejectsMissingEndOfCentralDirectory() {
        assertMalformed(new byte[22]);
    }

    @Test
    void rejectsInconsistentEndOfCentralDirectoryCommentLength() {
        byte[] source = new byte[22];
        source[0] = 0x50;
        source[1] = 0x4b;
        source[2] = 0x05;
        source[3] = 0x06;
        source[20] = 0x01;

        assertMalformed(source);
    }

    private static void assertMalformed(byte[] source) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeOoxmlRelationshipPreflight.requireNoExternalRelationships(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source OOXML relationship part is invalid", failure.getMessage());
    }

    private static OfficeConversionRequest request(byte[] source) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("8d0fc0f7-971f-4fde-9188-b71a8bfbbdb4"),
                17L,
                "docx",
                "policy-v1",
                "trace-ooxml-relationship-coverage",
                source,
                1_000_000L,
                10
        );
    }

    private static byte[] relationshipZip() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                + "Target=\"media/image1.png\"/>"
                + "</Relationships>";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static int centralDirectoryOffset(byte[] source) {
        int eocdOffset = findSignature(source, new byte[] {0x50, 0x4b, 0x05, 0x06});
        return (int) unsignedInt(source, eocdOffset + 16);
    }

    private static int findSignature(byte[] source, byte[] signature) {
        for (int offset = source.length - signature.length; offset >= 0; offset--) {
            boolean matches = true;
            for (int index = 0; index < signature.length; index++) {
                if (source[offset + index] != signature[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return offset;
            }
        }
        throw new IllegalStateException("ZIP signature not found in fixture");
    }

    private static int unsignedShort(byte[] source, int offset) {
        return Byte.toUnsignedInt(source[offset])
                | (Byte.toUnsignedInt(source[offset + 1]) << 8);
    }

    private static long unsignedInt(byte[] source, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(source[offset])
                        | (Byte.toUnsignedInt(source[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(source[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(source[offset + 3]) << 24)
        );
    }

    private static void putUnsignedInt(byte[] source, int offset, long value) {
        source[offset] = (byte) value;
        source[offset + 1] = (byte) (value >>> 8);
        source[offset + 2] = (byte) (value >>> 16);
        source[offset + 3] = (byte) (value >>> 24);
    }
}
