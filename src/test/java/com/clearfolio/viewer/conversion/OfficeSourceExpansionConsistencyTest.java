package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Expansion-consistency regressions for ZIP-family Office candidates.
 *
 * <p>A Deflate entry that claims non-empty expanded content cannot have an empty
 * compressed payload. Rejecting that impossible metadata combination before a
 * converter sees the package is a narrow prerequisite for the broader
 * decompression-ratio and archive-expansion policy required by issue #5.</p>
 */
class OfficeSourceExpansionConsistencyTest {

    private static final byte[] ENTRY_NAME = "word/document.xml".getBytes(StandardCharsets.UTF_8);
    private static final int LOCAL_FIXED_LENGTH = 30;
    private static final int CENTRAL_OFFSET = LOCAL_FIXED_LENGTH + ENTRY_NAME.length;
    private static final int CENTRAL_FIXED_LENGTH = 46;
    private static final int CENTRAL_RECORD_LENGTH = CENTRAL_FIXED_LENGTH + ENTRY_NAME.length;
    private static final int EOCD_OFFSET = CENTRAL_OFFSET + CENTRAL_RECORD_LENGTH;
    private static final int EOCD_LENGTH = 22;

    @Test
    void adapterRejectsNonEmptyDeflateClaimWithEmptyCompressedPayload() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = impossibleEmptyDeflateZip();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP deflated entry sizes are inconsistent", failure.getMessage());
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
                UUID.fromString("d846829d-b07e-4c28-a795-b8ca5c65b379"),
                12L,
                "docx",
                "policy-v1",
                "trace-expansion-consistency",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] impossibleEmptyDeflateZip() {
        byte[] bytes = new byte[EOCD_OFFSET + EOCD_LENGTH];

        putSignature(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 4, 20);
        putUnsignedShort(bytes, 8, 8);
        putUnsignedInt(bytes, 18, 0L);
        putUnsignedInt(bytes, 22, 1L);
        putUnsignedShort(bytes, 26, ENTRY_NAME.length);
        System.arraycopy(ENTRY_NAME, 0, bytes, LOCAL_FIXED_LENGTH, ENTRY_NAME.length);

        putSignature(bytes, CENTRAL_OFFSET, 0x02014b50L);
        putUnsignedShort(bytes, CENTRAL_OFFSET + 10, 8);
        putUnsignedInt(bytes, CENTRAL_OFFSET + 20, 0L);
        putUnsignedInt(bytes, CENTRAL_OFFSET + 24, 1L);
        putUnsignedShort(bytes, CENTRAL_OFFSET + 28, ENTRY_NAME.length);
        putUnsignedInt(bytes, CENTRAL_OFFSET + 42, 0L);
        System.arraycopy(
                ENTRY_NAME,
                0,
                bytes,
                CENTRAL_OFFSET + CENTRAL_FIXED_LENGTH,
                ENTRY_NAME.length
        );

        putSignature(bytes, EOCD_OFFSET, 0x06054b50L);
        putUnsignedShort(bytes, EOCD_OFFSET + 8, 1);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 1);
        putUnsignedInt(bytes, EOCD_OFFSET + 12, CENTRAL_RECORD_LENGTH);
        putUnsignedInt(bytes, EOCD_OFFSET + 16, CENTRAL_OFFSET);
        return bytes;
    }

    private static void putSignature(byte[] bytes, int offset, long value) {
        putUnsignedInt(bytes, offset, value);
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
