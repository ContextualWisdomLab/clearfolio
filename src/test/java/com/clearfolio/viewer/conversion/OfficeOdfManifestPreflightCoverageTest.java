package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/** Exercises fail-closed ODF manifest framing, extraction, and XML-policy boundaries. */
class OfficeOdfManifestPreflightCoverageTest {

    private static final String NS = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";
    private static final String MANIFEST_NAME = "META-INF/manifest.xml";
    private static final byte[] ODT_MIMETYPE =
            "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_MANIFEST_BYTES = 1_048_576;

    @Test
    void nonOdfFormatDoesNotEnterManifestParser() {
        OfficeConversionRequest request = request("docx", new byte[] {0x01});
        assertDoesNotThrow(() -> OfficeOdfManifestPreflight.requireQualifiedManifest(request));
    }

    @Test
    void malformedZipFramingFailsClosedBeforeXmlParsing() {
        assertInvalid(new byte[] {0x01}, "source ODF manifest is invalid");

        byte[] hugeCentralOffset = eocdOnly(1, 0x8000_0000L);
        assertInvalid(hugeCentralOffset, "source ODF manifest is invalid");

        byte[] missingCentralHeader = eocdOnly(1, 0L);
        assertInvalid(missingCentralHeader, "source ODF manifest is invalid");
    }

    @Test
    void malformedCentralEntryBoundsFailClosed() throws IOException {
        byte[] base = storedManifestPackage(validManifest("<manifest:file-entry manifest:full-path=\"Pictures/\"/>"));
        int central = findSignature(base, 0x02014b50L);

        byte[] oversizedName = base.clone();
        putUnsignedShort(oversizedName, central + 28, 0xffff);
        assertInvalid(oversizedName, "source ODF manifest is invalid");

        byte[] hugeLocalOffset = base.clone();
        putUnsignedInt(hugeLocalOffset, central + 42, 0x8000_0000L);
        assertInvalid(hugeLocalOffset, "source ODF manifest is invalid");

        byte[] outOfBoundsLocalOffset = base.clone();
        putUnsignedInt(outOfBoundsLocalOffset, central + 42, base.length);
        assertInvalid(outOfBoundsLocalOffset, "source ODF manifest is invalid");
    }

    @Test
    void manifestExtractionEnforcesDeclaredBoundsAndCompression() throws IOException {
        byte[] base = storedManifestPackage(validManifest("<manifest:file-entry manifest:full-path=\"Pictures/\"/>"));
        int central = findSignature(base, 0x02014b50L);
        long payloadLength = unsignedInt(base, central + 24);

        byte[] tooLarge = base.clone();
        putUnsignedInt(tooLarge, central + 24, MAX_MANIFEST_BYTES + 1L);
        assertInvalid(tooLarge, "source ODF manifest exceeds maximum bytes", OfficeConversionFailureCode.POLICY_DENIED);

        byte[] compressedTooLarge = base.clone();
        putUnsignedInt(compressedTooLarge, central + 20, 0x8000_0000L);
        assertInvalid(compressedTooLarge, "source ODF manifest is invalid");

        byte[] dataPastEnd = base.clone();
        putUnsignedInt(dataPastEnd, central + 20, base.length);
        putUnsignedInt(dataPastEnd, central + 24, base.length);
        assertInvalid(dataPastEnd, "source ODF manifest is invalid");

        byte[] storedSizeMismatch = base.clone();
        putUnsignedInt(storedSizeMismatch, central + 24, payloadLength + 1L);
        assertInvalid(storedSizeMismatch, "source ODF manifest is invalid");

        byte[] unsupportedMethod = base.clone();
        putUnsignedShort(unsupportedMethod, central + 10, 99);
        assertInvalid(unsupportedMethod, "source ODF manifest is invalid");
    }

    @Test
    void deflatedManifestRoundTripsAndCorruptionFailsClosed() throws IOException {
        byte[] valid = deflatedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"Pictures/\"/>"));
        assertDoesNotThrow(() -> OfficeOdfManifestPreflight.requireQualifiedManifest(request("odt", valid)));

        byte[] corrupt = valid.clone();
        int local = findSignature(corrupt, 0x04034b50L);
        int central = findSignature(corrupt, 0x02014b50L);
        int nameLength = unsignedShort(corrupt, local + 26);
        int extraLength = unsignedShort(corrupt, local + 28);
        int dataOffset = local + 30 + nameLength + extraLength;
        int compressedSize = (int) unsignedInt(corrupt, central + 20);
        Arrays.fill(corrupt, dataOffset, dataOffset + compressedSize, (byte) 0x7f);
        assertInvalid(corrupt, "source ODF manifest is invalid");
    }

    @Test
    void manifestXmlRequiresQualifiedRootAndFilePath() throws IOException {
        assertInvalid(storedManifestPackage(("<manifest xmlns=\"urn:wrong\"/>")
                .getBytes(StandardCharsets.UTF_8)), "source ODF manifest is invalid");
        assertInvalid(storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:media-type=\"text/xml\"/>")),
                "source ODF manifest is invalid");
        assertInvalid(storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"\"/>")),
                "source ODF manifest is invalid");
    }

    @Test
    void manifestRejectsDuplicateRootAndReservedSelfReferences() throws IOException {
        assertInvalid(storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>"
                        + "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>")),
                "source ODF manifest is invalid");
        assertInvalid(storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"META-INF/manifest.xml\"/>")),
                "source ODF manifest does not match package file inventory");
        assertInvalid(storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"mimetype\"/>")),
                "source ODF manifest does not match package file inventory");
    }

    @Test
    void mimetypeAndManifestRootMustAppearTogether() throws IOException {
        byte[] mimetypeWithoutRoot = packageWithEntries(
                validManifest("<manifest:file-entry manifest:full-path=\"Pictures/\"/>"),
                true,
                null,
                false
        );
        assertInvalid(mimetypeWithoutRoot, "source ODF manifest root entry is missing");

        byte[] rootWithoutMimetype = storedManifestPackage(validManifest(
                "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>"));
        assertInvalid(rootWithoutMimetype, "source ODF mimetype entry is missing for manifest root");
    }

    @Test
    void duplicateOrdinaryManifestEntryFailsInventoryCardinality() throws IOException {
        String duplicate = "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>";
        byte[] source = packageWithEntries(validManifest(duplicate), false, "content.xml", false);

        assertInvalid(source, "source ODF manifest does not match package file inventory");
    }

    @Test
    void malformedXmlAndDtdAreRejectedWithoutExternalResolution() throws IOException {
        assertInvalid(storedManifestPackage("<manifest:manifest".getBytes(StandardCharsets.UTF_8)),
                "source ODF manifest is invalid");
        String dtd = "<!DOCTYPE manifest:manifest [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + new String(validManifest(
                        "<manifest:file-entry manifest:full-path=\"Pictures/\"/>"),
                        StandardCharsets.UTF_8);
        assertInvalid(storedManifestPackage(dtd.getBytes(StandardCharsets.UTF_8)),
                "source ODF manifest is invalid");
    }

    @Test
    void packageWithoutManifestEntryFailsClosed() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeStored(zip, "content.xml", "content".getBytes(StandardCharsets.UTF_8));
        }
        assertInvalid(output.toByteArray(), "source ODF manifest is invalid");
    }

    private static void assertInvalid(byte[] source, String message) {
        assertInvalid(source, message, OfficeConversionFailureCode.MALFORMED_INPUT);
    }

    private static void assertInvalid(
            byte[] source,
            String message,
            OfficeConversionFailureCode failureCode
    ) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeOdfManifestPreflight.requireQualifiedManifest(request("odt", source))
        );
        assertEquals(failureCode, failure.failureCode());
        assertEquals(message, failure.getMessage());
    }

    private static OfficeConversionRequest request(String format, byte[] source) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("7da8a7e7-3e3b-4d89-9ef5-36fd05955589"),
                17L,
                format,
                "policy-v1",
                "trace-odf-preflight-coverage",
                source,
                2_000_000L,
                20
        );
    }

    private static byte[] validManifest(String body) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest xmlns:manifest=\"" + NS + "\" manifest:version=\"1.4\">"
                + body
                + "</manifest:manifest>").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] storedManifestPackage(byte[] manifest) throws IOException {
        return packageWithEntries(manifest, false, null, false);
    }

    private static byte[] deflatedManifestPackage(byte[] manifest) throws IOException {
        return packageWithEntries(manifest, false, null, true);
    }

    private static byte[] packageWithEntries(
            byte[] manifest,
            boolean includeMimetype,
            String ordinaryFile,
            boolean deflateManifest
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (includeMimetype) {
                writeStored(zip, "mimetype", ODT_MIMETYPE);
            }
            if (deflateManifest) {
                ZipEntry entry = new ZipEntry(MANIFEST_NAME);
                entry.setMethod(ZipEntry.DEFLATED);
                zip.putNextEntry(entry);
                zip.write(manifest);
                zip.closeEntry();
            } else {
                writeStored(zip, MANIFEST_NAME, manifest);
            }
            if (ordinaryFile != null) {
                writeStored(zip, ordinaryFile, "content".getBytes(StandardCharsets.UTF_8));
            }
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

    private static byte[] eocdOnly(int entryCount, long centralOffset) {
        byte[] bytes = new byte[22];
        putUnsignedInt(bytes, 0, 0x06054b50L);
        putUnsignedShort(bytes, 8, entryCount);
        putUnsignedShort(bytes, 10, entryCount);
        putUnsignedInt(bytes, 16, centralOffset);
        return bytes;
    }

    private static int findSignature(byte[] bytes, long signature) {
        for (int offset = 0; offset <= bytes.length - 4; offset++) {
            if (unsignedInt(bytes, offset) == signature) {
                return offset;
            }
        }
        throw new IllegalStateException("ZIP signature not found");
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
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
