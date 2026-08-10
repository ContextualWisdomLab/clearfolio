package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Verifies the OpenDocument manifest root media type against the package mimetype entry.
 */
class OfficeOdfManifestMediaTypePolicyTest {

    private static final byte[] MANIFEST_NAME =
            "META-INF/manifest.xml".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MIMETYPE_NAME = "mimetype".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ODT_MIMETYPE =
            "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
    private static final String ODS_MIMETYPE = "application/vnd.oasis.opendocument.spreadsheet";
    private static final int LOCAL_HEADER_LENGTH = 30;
    private static final int CENTRAL_HEADER_LENGTH = 46;

    @Test
    void adapterRejectsManifestRootMediaTypeThatDisagreesWithMimetype() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfZipWithRootMediaType(ODS_MIMETYPE);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF manifest root media type does not match mimetype", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAcceptsManifestRootMediaTypeThatMatchesMimetype() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfZipWithRootMediaType(new String(ODT_MIMETYPE, StandardCharsets.US_ASCII));

        countingAdapter(providerCalls).convert(request(source));

        assertEquals(1, providerCalls.get());
    }

    private static OfficeConversionAdapter countingAdapter(AtomicInteger providerCalls) {
        return input -> {
            providerCalls.incrementAndGet();
            return new OfficeConversionResult(
                    "deterministic-fixture",
                    "1",
                    input.sourceSha256(),
                    input.binding(),
                    OfficeConversionTestPdf.onePage()
            );
        };
    }

    private static OfficeConversionRequest request(byte[] sourceBytes) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("218688bd-713c-4a37-87fc-54b25f73db07"),
                10L,
                "odt",
                "policy-v1",
                "trace-odf-manifest-media-type",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] odfZipWithRootMediaType(String rootMediaType) {
        byte[] manifestPayload = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest "
                + "xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" "
                + "manifest:version=\"1.4\">"
                + "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\""
                + rootMediaType
                + "\"/>"
                + "</manifest:manifest>").getBytes(StandardCharsets.UTF_8);

        int mimetypeLocalOffset = 0;
        int mimetypeDataOffset = mimetypeLocalOffset + LOCAL_HEADER_LENGTH + MIMETYPE_NAME.length;
        int manifestLocalOffset = mimetypeDataOffset + ODT_MIMETYPE.length;
        int manifestDataOffset = manifestLocalOffset + LOCAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int centralOffset = manifestDataOffset + manifestPayload.length;
        int mimetypeCentralLength = CENTRAL_HEADER_LENGTH + MIMETYPE_NAME.length;
        int manifestCentralOffset = centralOffset + mimetypeCentralLength;
        int manifestCentralLength = CENTRAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int centralLength = mimetypeCentralLength + manifestCentralLength;
        int eocdOffset = centralOffset + centralLength;
        byte[] bytes = new byte[eocdOffset + 22];

        writeLocalHeader(bytes, mimetypeLocalOffset, MIMETYPE_NAME, ODT_MIMETYPE.length);
        System.arraycopy(ODT_MIMETYPE, 0, bytes, mimetypeDataOffset, ODT_MIMETYPE.length);
        writeLocalHeader(bytes, manifestLocalOffset, MANIFEST_NAME, manifestPayload.length);
        System.arraycopy(manifestPayload, 0, bytes, manifestDataOffset, manifestPayload.length);

        writeCentralHeader(
                bytes,
                centralOffset,
                MIMETYPE_NAME,
                mimetypeLocalOffset,
                ODT_MIMETYPE.length
        );
        writeCentralHeader(
                bytes,
                manifestCentralOffset,
                MANIFEST_NAME,
                manifestLocalOffset,
                manifestPayload.length
        );
        writeEocd(bytes, eocdOffset, 2, centralLength, centralOffset);
        return bytes;
    }

    private static void writeLocalHeader(byte[] bytes, int offset, byte[] entryName, int size) {
        putUnsignedInt(bytes, offset, 0x04034b50L);
        putUnsignedShort(bytes, offset + 4, 20);
        putUnsignedShort(bytes, offset + 8, 0);
        putUnsignedInt(bytes, offset + 14, 0L);
        putUnsignedInt(bytes, offset + 18, size);
        putUnsignedInt(bytes, offset + 22, size);
        putUnsignedShort(bytes, offset + 26, entryName.length);
        putUnsignedShort(bytes, offset + 28, 0);
        System.arraycopy(entryName, 0, bytes, offset + LOCAL_HEADER_LENGTH, entryName.length);
    }

    private static void writeCentralHeader(
            byte[] bytes,
            int offset,
            byte[] entryName,
            int localHeaderOffset,
            int size
    ) {
        putUnsignedInt(bytes, offset, 0x02014b50L);
        putUnsignedShort(bytes, offset + 10, 0);
        putUnsignedInt(bytes, offset + 16, 0L);
        putUnsignedInt(bytes, offset + 20, size);
        putUnsignedInt(bytes, offset + 24, size);
        putUnsignedShort(bytes, offset + 28, entryName.length);
        putUnsignedInt(bytes, offset + 42, localHeaderOffset);
        System.arraycopy(entryName, 0, bytes, offset + CENTRAL_HEADER_LENGTH, entryName.length);
    }

    private static void writeEocd(
            byte[] bytes,
            int eocdOffset,
            int entryCount,
            int centralLength,
            int centralOffset
    ) {
        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, entryCount);
        putUnsignedShort(bytes, eocdOffset + 10, entryCount);
        putUnsignedInt(bytes, eocdOffset + 12, centralLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
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
