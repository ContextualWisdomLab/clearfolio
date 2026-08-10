package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Enforces deterministic placement rules for an optional OpenDocument mimetype entry.
 */
class OfficeOdfMimetypePolicyTest {

    private static final byte[] MANIFEST_NAME =
            "META-INF/manifest.xml".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MIMETYPE_NAME = "mimetype".getBytes(StandardCharsets.UTF_8);
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

        writeLocalHeader(bytes, manifestLocalOffset, MANIFEST_NAME);
        writeLocalHeader(bytes, mimetypeLocalOffset, MIMETYPE_NAME);
        writeCentralHeader(bytes, centralOffset, MANIFEST_NAME, manifestLocalOffset);
        writeCentralHeader(bytes, secondCentralOffset, MIMETYPE_NAME, mimetypeLocalOffset);

        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, 2);
        putUnsignedShort(bytes, eocdOffset + 10, 2);
        putUnsignedInt(bytes, eocdOffset + 12, centralLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
        return bytes;
    }

    private static void writeLocalHeader(byte[] bytes, int offset, byte[] entryName) {
        putUnsignedInt(bytes, offset, 0x04034b50L);
        putUnsignedShort(bytes, offset + 4, 20);
        putUnsignedShort(bytes, offset + 26, entryName.length);
        System.arraycopy(entryName, 0, bytes, offset + LOCAL_HEADER_LENGTH, entryName.length);
    }

    private static void writeCentralHeader(
            byte[] bytes,
            int offset,
            byte[] entryName,
            int localHeaderOffset
    ) {
        putUnsignedInt(bytes, offset, 0x02014b50L);
        putUnsignedShort(bytes, offset + 28, entryName.length);
        putUnsignedInt(bytes, offset + 42, localHeaderOffset);
        System.arraycopy(entryName, 0, bytes, offset + CENTRAL_HEADER_LENGTH, entryName.length);
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
