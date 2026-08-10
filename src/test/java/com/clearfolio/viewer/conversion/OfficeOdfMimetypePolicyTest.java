package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Enforces deterministic placement and storage rules for an optional OpenDocument mimetype entry.
 */
class OfficeOdfMimetypePolicyTest {

    private static final byte[] MANIFEST_NAME =
            "META-INF/manifest.xml".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MIMETYPE_NAME = "mimetype".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ODT_MIMETYPE =
            "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
    private static final int LOCAL_HEADER_LENGTH = 30;
    private static final int CENTRAL_HEADER_LENGTH = 46;

    @Test
    void adapterRejectsOdfMimetypeEntryWhenItIsNotFirst() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfZipWithMimetypeSecond();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF mimetype entry must be first", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsOdfMimetypeEntryWhenCompressed() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfZipWithFirstMimetype(8, 0);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF mimetype entry must be stored without compression", failure.getMessage());
        assertEquals(0, providerCalls.get());
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
                "trace-odf-mimetype-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] odfZipWithMimetypeSecond() {
        int manifestLocalOffset = 0;
        int mimetypeLocalOffset = LOCAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int centralOffset = mimetypeLocalOffset + LOCAL_HEADER_LENGTH + MIMETYPE_NAME.length;
        int firstCentralLength = CENTRAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int secondCentralOffset = centralOffset + firstCentralLength;
        int secondCentralLength = CENTRAL_HEADER_LENGTH + MIMETYPE_NAME.length;
        int centralLength = firstCentralLength + secondCentralLength;
        int eocdOffset = centralOffset + centralLength;
        byte[] bytes = new byte[eocdOffset + 22];

        writeLocalHeader(bytes, manifestLocalOffset, MANIFEST_NAME, 0, 0, 0);
        writeLocalHeader(bytes, mimetypeLocalOffset, MIMETYPE_NAME, 0, 0, 0);
        writeCentralHeader(bytes, centralOffset, MANIFEST_NAME, manifestLocalOffset, 0, 0, 0);
        writeCentralHeader(bytes, secondCentralOffset, MIMETYPE_NAME, mimetypeLocalOffset, 0, 0, 0);

        writeEocd(bytes, eocdOffset, 2, centralLength, centralOffset);
        return bytes;
    }

    private static byte[] odfZipWithFirstMimetype(int compressionMethod, int localExtraFieldLength) {
        int mimetypeLocalOffset = 0;
        int mimetypeDataOffset = LOCAL_HEADER_LENGTH + MIMETYPE_NAME.length + localExtraFieldLength;
        int manifestLocalOffset = mimetypeDataOffset + ODT_MIMETYPE.length;
        int centralOffset = manifestLocalOffset + LOCAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int mimetypeCentralLength = CENTRAL_HEADER_LENGTH + MIMETYPE_NAME.length;
        int manifestCentralOffset = centralOffset + mimetypeCentralLength;
        int manifestCentralLength = CENTRAL_HEADER_LENGTH + MANIFEST_NAME.length;
        int centralLength = mimetypeCentralLength + manifestCentralLength;
        int eocdOffset = centralOffset + centralLength;
        byte[] bytes = new byte[eocdOffset + 22];

        writeLocalHeader(
                bytes,
                mimetypeLocalOffset,
                MIMETYPE_NAME,
                compressionMethod,
                ODT_MIMETYPE.length,
                localExtraFieldLength
        );
        System.arraycopy(ODT_MIMETYPE, 0, bytes, mimetypeDataOffset, ODT_MIMETYPE.length);
        writeLocalHeader(bytes, manifestLocalOffset, MANIFEST_NAME, 0, 0, 0);
        writeCentralHeader(
                bytes,
                centralOffset,
                MIMETYPE_NAME,
                mimetypeLocalOffset,
                compressionMethod,
                ODT_MIMETYPE.length,
                ODT_MIMETYPE.length
        );
        writeCentralHeader(bytes, manifestCentralOffset, MANIFEST_NAME, manifestLocalOffset, 0, 0, 0);

        writeEocd(bytes, eocdOffset, 2, centralLength, centralOffset);
        return bytes;
    }

    private static void writeLocalHeader(
            byte[] bytes,
            int offset,
            byte[] entryName,
            int compressionMethod,
            int size,
            int extraFieldLength
    ) {
        putUnsignedInt(bytes, offset, 0x04034b50L);
        putUnsignedShort(bytes, offset + 4, 20);
        putUnsignedShort(bytes, offset + 8, compressionMethod);
        putUnsignedInt(bytes, offset + 18, size);
        putUnsignedInt(bytes, offset + 22, size);
        putUnsignedShort(bytes, offset + 26, entryName.length);
        putUnsignedShort(bytes, offset + 28, extraFieldLength);
        System.arraycopy(entryName, 0, bytes, offset + LOCAL_HEADER_LENGTH, entryName.length);
    }

    private static void writeCentralHeader(
            byte[] bytes,
            int offset,
            byte[] entryName,
            int localHeaderOffset,
            int compressionMethod,
            int compressedSize,
            int uncompressedSize
    ) {
        putUnsignedInt(bytes, offset, 0x02014b50L);
        putUnsignedShort(bytes, offset + 10, compressionMethod);
        putUnsignedInt(bytes, offset + 20, compressedSize);
        putUnsignedInt(bytes, offset + 24, uncompressedSize);
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
