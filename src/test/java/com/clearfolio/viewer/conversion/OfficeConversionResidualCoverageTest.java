package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;

/** Covers residual fail-closed branches in Office package and action preflight. */
class OfficeConversionResidualCoverageTest {

    private static final int LOCAL_FIXED = 30;
    private static final int CENTRAL_FIXED = 46;
    private static final int EOCD_LENGTH = 22;
    private static final byte[] ODT_MIMETYPE =
            "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
    private static final String ODF_MANIFEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" "
            + "manifest:version=\"1.4\">"
            + "<manifest:file-entry manifest:full-path=\"/\" "
            + "manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>"
            + "</manifest:manifest>";

    @Test
    void repeatedActionIdentityFailsClosedBeforeFollowingCycle() throws Exception {
        COSDictionary action = new COSDictionary();
        action.setItem(COSName.getPDFName("S"), COSName.getPDFName("GoTo"));
        action.setItem(COSName.getPDFName("D"), COSName.getPDFName("section-one"));
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(action);

        Method method = OfficeConversionAdapter.class.getDeclaredMethod(
                "isProhibitedAction",
                COSBase.class,
                boolean.class,
                Set.class,
                int.class
        );
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, action, false, visited, 1));
    }

    @Test
    void emptyDeflateInputFailsClosedWithoutSpinning() throws IOException {
        byte[] source = deflatedOdfPackage();
        int central = findSignature(source, 0x02014b50L);
        putUnsignedInt(source, central + 20, 0L);

        assertMalformedManifest(source);
    }

    @Test
    void manifestLocatorRejectsSecondCentralCursorOutsideSignatureBounds() {
        byte[] source = new byte[70];
        putUnsignedInt(source, 0, 0x02014b50L);
        putUnsignedShort(source, 28, 22);
        int eocd = 48;
        putUnsignedInt(source, eocd, 0x06054b50L);
        putUnsignedShort(source, eocd + 8, 2);
        putUnsignedShort(source, eocd + 10, 2);
        putUnsignedInt(source, eocd + 16, 0L);

        assertMalformedManifest(source);
    }

    @Test
    void sourceContainerRejectsMissingSecondCentralRecord() {
        byte[] source = oneEntryZip("content.xml");
        int eocd = source.length - EOCD_LENGTH;
        putUnsignedShort(source, eocd + 8, 2);
        putUnsignedShort(source, eocd + 10, 2);

        assertMalformedContainer("docx", source);
    }

    @Test
    void sourceContainerRejectsUnaccountedCentralDirectoryPadding() {
        byte[] base = oneEntryZip("content.xml");
        int oldEocd = base.length - EOCD_LENGTH;
        byte[] source = new byte[base.length + 1];
        System.arraycopy(base, 0, source, 0, oldEocd);
        System.arraycopy(base, oldEocd, source, oldEocd + 1, EOCD_LENGTH);
        int newEocd = oldEocd + 1;
        putUnsignedInt(source, newEocd + 12, centralRecordLength("content.xml") + 1L);

        assertMalformedContainer("docx", source);
    }

    @Test
    void sourceContainerAcceptsMatchingDataDescriptorAuthority() {
        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(
                request("docx", oneEntryZipWithDescriptor("content.xml"))
        ));
    }

    @Test
    void odfMimetypePayloadMustMatchWhenDeclaredLengthMatches() throws IOException {
        byte[] source = storedOdfPackage();
        int localNameLength = unsignedShort(source, 26);
        int localExtraLength = unsignedShort(source, 28);
        int mimetypeData = LOCAL_FIXED + localNameLength + localExtraLength;
        source[mimetypeData] ^= 0x01;

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("odt", source))
        );
        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF mimetype payload is invalid", failure.getMessage());
    }

    @Test
    void equalLengthNearManifestNameExercisesByteMismatchPath() {
        byte[] source = oneEntryZip("META-INF/manifest.xmL");

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("odt", source))
        );
        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF META-INF entry is not allowed", failure.getMessage());
    }

    @Test
    void safePathAcceptsShortAndNonLetterLeadingNames() {
        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(
                request("docx", oneEntryZip("a"))
        ));
        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(
                request("docx", oneEntryZip("1x"))
        ));
        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(
                request("docx", oneEntryZip("a/.x"))
        ));
    }

    private static void assertMalformedManifest(byte[] source) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeOdfManifestPreflight.requireQualifiedManifest(request("odt", source))
        );
        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF manifest is invalid", failure.getMessage());
    }

    private static void assertMalformedContainer(String format, byte[] source) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request(format, source))
        );
        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
    }

    private static OfficeConversionRequest request(String format, byte[] source) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("61d93e0c-c1b5-45c6-a1c1-29cff383977e"),
                23L,
                format,
                "policy-v1",
                "trace-residual-coverage",
                source,
                2_000_000L,
                20
        );
    }

    private static byte[] deflatedOdfPackage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry manifest = new ZipEntry("META-INF/manifest.xml");
            manifest.setMethod(ZipEntry.DEFLATED);
            zip.putNextEntry(manifest);
            zip.write(ODF_MANIFEST.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] storedOdfPackage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeStored(zip, "mimetype", ODT_MIMETYPE);
            writeStored(zip, "META-INF/manifest.xml", ODF_MANIFEST.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] payload) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(payload.length);
        entry.setCompressedSize(payload.length);
        entry.setCrc(crc32.getValue());
        zip.putNextEntry(entry);
        zip.write(payload);
        zip.closeEntry();
    }

    private static byte[] oneEntryZip(String entryName) {
        byte[] name = entryName.getBytes(StandardCharsets.ISO_8859_1);
        int centralOffset = LOCAL_FIXED + name.length;
        int centralRecordLength = CENTRAL_FIXED + name.length;
        int eocdOffset = centralOffset + centralRecordLength;
        byte[] bytes = new byte[eocdOffset + EOCD_LENGTH];

        putUnsignedInt(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 4, 20);
        putUnsignedShort(bytes, 26, name.length);
        System.arraycopy(name, 0, bytes, LOCAL_FIXED, name.length);

        putUnsignedInt(bytes, centralOffset, 0x02014b50L);
        putUnsignedShort(bytes, centralOffset + 28, name.length);
        putUnsignedInt(bytes, centralOffset + 42, 0L);
        System.arraycopy(name, 0, bytes, centralOffset + CENTRAL_FIXED, name.length);

        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, 1);
        putUnsignedShort(bytes, eocdOffset + 10, 1);
        putUnsignedInt(bytes, eocdOffset + 12, centralRecordLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
        return bytes;
    }

    private static byte[] oneEntryZipWithDescriptor(String entryName) {
        byte[] name = entryName.getBytes(StandardCharsets.ISO_8859_1);
        int descriptorOffset = LOCAL_FIXED + name.length;
        int descriptorLength = 16;
        int centralOffset = descriptorOffset + descriptorLength;
        int centralRecordLength = CENTRAL_FIXED + name.length;
        int eocdOffset = centralOffset + centralRecordLength;
        byte[] bytes = new byte[eocdOffset + EOCD_LENGTH];

        putUnsignedInt(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 4, 20);
        putUnsignedShort(bytes, 6, 0x0008);
        putUnsignedShort(bytes, 26, name.length);
        System.arraycopy(name, 0, bytes, LOCAL_FIXED, name.length);
        putUnsignedInt(bytes, descriptorOffset, 0x08074b50L);

        putUnsignedInt(bytes, centralOffset, 0x02014b50L);
        putUnsignedShort(bytes, centralOffset + 8, 0x0008);
        putUnsignedShort(bytes, centralOffset + 28, name.length);
        putUnsignedInt(bytes, centralOffset + 42, 0L);
        System.arraycopy(name, 0, bytes, centralOffset + CENTRAL_FIXED, name.length);

        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, 1);
        putUnsignedShort(bytes, eocdOffset + 10, 1);
        putUnsignedInt(bytes, eocdOffset + 12, centralRecordLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
        return bytes;
    }

    private static long centralRecordLength(String entryName) {
        return CENTRAL_FIXED + entryName.getBytes(StandardCharsets.ISO_8859_1).length;
    }

    private static int findSignature(byte[] source, long signature) {
        for (int offset = 0; offset <= source.length - 4; offset++) {
            if (unsignedInt(source, offset) == signature) {
                return offset;
            }
        }
        throw new IllegalArgumentException("signature not found");
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(bytes[offset])
                        | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(bytes[offset + 3]) << 24)
        );
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
}
