package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Verifies duplicated ZIP local-header metadata before an Office provider is invoked.
 */
class OfficeSourceLocalHeaderMetadataPolicyTest {

    private static final byte[] ENTRY_NAME = "content.xml".getBytes(StandardCharsets.UTF_8);
    private static final int LOCAL_HEADER_LENGTH = 30;
    private static final int CENTRAL_HEADER_LENGTH = 46;
    private static final int CENTRAL_OFFSET = LOCAL_HEADER_LENGTH + ENTRY_NAME.length;
    private static final int CENTRAL_RECORD_LENGTH = CENTRAL_HEADER_LENGTH + ENTRY_NAME.length;
    private static final int EOCD_OFFSET = CENTRAL_OFFSET + CENTRAL_RECORD_LENGTH;

    @Test
    void adapterRejectsLocalCompressedSizeMismatchWithoutDataDescriptor() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = oneEntryStoredZip();
        putUnsignedInt(source, 18, 1L);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP local header does not match central directory", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsDataDescriptorFlagMismatchBetweenLocalAndCentralRecords() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = oneEntryStoredZip();
        putUnsignedShort(source, CENTRAL_OFFSET + 8, 0x0008);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP local header does not match central directory", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsLocalCrcMismatchWithoutDataDescriptor() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = oneEntryStoredZip();
        putUnsignedInt(source, 14, 1L);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP local header does not match central directory", failure.getMessage());
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
                "docx",
                "policy-v1",
                "trace-local-header-metadata",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] oneEntryStoredZip() {
        byte[] bytes = new byte[EOCD_OFFSET + 22];
        putUnsignedInt(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 4, 20);
        putUnsignedShort(bytes, 26, ENTRY_NAME.length);
        System.arraycopy(ENTRY_NAME, 0, bytes, LOCAL_HEADER_LENGTH, ENTRY_NAME.length);

        putUnsignedInt(bytes, CENTRAL_OFFSET, 0x02014b50L);
        putUnsignedShort(bytes, CENTRAL_OFFSET + 28, ENTRY_NAME.length);
        putUnsignedInt(bytes, CENTRAL_OFFSET + 42, 0L);
        System.arraycopy(ENTRY_NAME, 0, bytes, CENTRAL_OFFSET + CENTRAL_HEADER_LENGTH, ENTRY_NAME.length);

        putUnsignedInt(bytes, EOCD_OFFSET, 0x06054b50L);
        putUnsignedShort(bytes, EOCD_OFFSET + 8, 1);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 1);
        putUnsignedInt(bytes, EOCD_OFFSET + 12, CENTRAL_RECORD_LENGTH);
        putUnsignedInt(bytes, EOCD_OFFSET + 16, CENTRAL_OFFSET);
        return bytes;
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
