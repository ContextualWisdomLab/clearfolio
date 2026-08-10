package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Central-directory metadata regressions for ZIP-family Office candidates.
 *
 * <p>The pre-provider boundary must not trust the EOCD entry count or a leading
 * central-directory signature alone. These tests do not decompress entry data;
 * they require matching local/central metadata with a safe relative entry name
 * and fail closed when a central record advertises ZIP encryption.</p>
 */
class OfficeSourceCentralDirectoryPolicyTest {

    private static final byte[] SAFE_ENTRY_NAME = "content.xml".getBytes(StandardCharsets.UTF_8);
    private static final int LOCAL_HEADER_OFFSET = 0;
    private static final int LOCAL_HEADER_FIXED_LENGTH = 30;
    private static final int CENTRAL_DIRECTORY_OFFSET = LOCAL_HEADER_FIXED_LENGTH + SAFE_ENTRY_NAME.length;
    private static final int CENTRAL_DIRECTORY_FIXED_LENGTH = 46;
    private static final int CENTRAL_DIRECTORY_RECORD_LENGTH =
            CENTRAL_DIRECTORY_FIXED_LENGTH + SAFE_ENTRY_NAME.length;
    private static final int EOCD_OFFSET = CENTRAL_DIRECTORY_OFFSET + CENTRAL_DIRECTORY_RECORD_LENGTH;
    private static final int EOCD_LENGTH = 22;

    @Test
    void adapterRejectsEncryptedCentralDirectoryEntryBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = oneEntryZip(1, 1);
        putUnsignedShort(source, CENTRAL_DIRECTORY_OFFSET + 8, 0x0001);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.PASSWORD_PROTECTED, failure.failureCode());
        assertEquals("source ZIP entry is encrypted", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsEocdCountThatExceedsPresentCentralRecords() {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = oneEntryZip(2, 2);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP central directory is invalid", failure.getMessage());
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
                "trace-central-directory-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] oneEntryZip(int entriesOnDisk, int totalEntries) {
        byte[] bytes = new byte[EOCD_OFFSET + EOCD_LENGTH];
        putSignature(bytes, LOCAL_HEADER_OFFSET, 0x04034b50L);
        putUnsignedShort(bytes, LOCAL_HEADER_OFFSET + 4, 20);
        putUnsignedShort(bytes, LOCAL_HEADER_OFFSET + 26, SAFE_ENTRY_NAME.length);
        System.arraycopy(
                SAFE_ENTRY_NAME,
                0,
                bytes,
                LOCAL_HEADER_OFFSET + LOCAL_HEADER_FIXED_LENGTH,
                SAFE_ENTRY_NAME.length
        );

        putSignature(bytes, CENTRAL_DIRECTORY_OFFSET, 0x02014b50L);
        putUnsignedShort(bytes, CENTRAL_DIRECTORY_OFFSET + 28, SAFE_ENTRY_NAME.length);
        putUnsignedInt(bytes, CENTRAL_DIRECTORY_OFFSET + 42, LOCAL_HEADER_OFFSET);
        System.arraycopy(
                SAFE_ENTRY_NAME,
                0,
                bytes,
                CENTRAL_DIRECTORY_OFFSET + CENTRAL_DIRECTORY_FIXED_LENGTH,
                SAFE_ENTRY_NAME.length
        );
        putSignature(bytes, EOCD_OFFSET, 0x06054b50L);
        putUnsignedShort(bytes, EOCD_OFFSET + 8, entriesOnDisk);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, totalEntries);
        putUnsignedInt(bytes, EOCD_OFFSET + 12, CENTRAL_DIRECTORY_RECORD_LENGTH);
        putUnsignedInt(bytes, EOCD_OFFSET + 16, CENTRAL_DIRECTORY_OFFSET);
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
