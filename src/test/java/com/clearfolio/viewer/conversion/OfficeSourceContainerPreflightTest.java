package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Source-container regressions for the Office adapter trust boundary.
 *
 * <p>These tests deliberately cover only the common pre-conversion authority:
 * candidate format qualification, declared-format/container-family agreement,
 * and bounded ZIP central-directory framing. They do not treat passing this
 * preflight as complete safety, Office-package, archive-expansion, macro,
 * malware, or fidelity qualification.</p>
 */
class OfficeSourceContainerPreflightTest {

    private static final byte[] ZIP_LOCAL_HEADER = new byte[] {
            0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00
    };
    private static final byte[] COMPOUND_FILE_HEADER = new byte[] {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };
    private static final int CENTRAL_OFFSET = 8;
    private static final int EOCD_OFFSET = 12;

    @Test
    void adapterRejectsUnknownFormatBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request("pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
        );

        assertEquals(OfficeConversionFailureCode.UNSUPPORTED_FORMAT, failure.failureCode());
        assertEquals("source format is not an Office conversion candidate", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsZipFamilyWithCompoundFileSignatureBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request("docx", COMPOUND_FILE_HEADER))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source container signature does not match declared format", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsLegacyFamilyWithZipSignatureBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request("xls", framedZip()))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source container signature does not match declared format", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsTruncatedZipSignatureBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request("docx", new byte[] {0x50, 0x4b, 0x03}))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source container signature does not match declared format", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsZipPrefixWithoutCentralDirectoryFramingBeforeProviderInvocation() {
        assertMalformedZipBeforeProvider(ZIP_LOCAL_HEADER);
    }

    @Test
    void adapterRejectsLongZipCandidateWithoutEocdBeforeProviderInvocation() {
        byte[] bytes = new byte[40];
        System.arraycopy(ZIP_LOCAL_HEADER, 0, bytes, 0, ZIP_LOCAL_HEADER.length);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsEocdWithCommentLengthBeyondBuffer() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 20, 1);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsMultiDiskZipFraming() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 4, 1);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsCentralDirectoryOnDifferentDisk() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 6, 1);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsZeroEntryZipFraming() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 8, 0);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 0);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsMismatchedEntryCounts() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 2);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsZip64EntrySentinelWithoutSeparateQualification() {
        byte[] bytes = framedZip();
        putUnsignedShort(bytes, EOCD_OFFSET + 8, 0xffff);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 0xffff);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsZip64CentralDirectorySizeSentinel() {
        byte[] bytes = framedZip();
        putUnsignedInt(bytes, EOCD_OFFSET + 12, 0xffff_ffffL);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsZip64CentralDirectoryOffsetSentinel() {
        byte[] bytes = framedZip();
        putUnsignedInt(bytes, EOCD_OFFSET + 16, 0xffff_ffffL);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsEmptyCentralDirectorySize() {
        byte[] bytes = framedZip();
        putUnsignedInt(bytes, EOCD_OFFSET + 12, 0L);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsCentralDirectoryOffsetOutsideAddressableInput() {
        byte[] bytes = framedZip();
        putUnsignedInt(bytes, EOCD_OFFSET + 16, 0x8000_0000L);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsCentralDirectoryThatOverlapsEocd() {
        byte[] bytes = framedZip();
        putUnsignedInt(bytes, EOCD_OFFSET + 12, 8L);

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterRejectsMissingCentralDirectorySignature() {
        byte[] bytes = framedZip();
        bytes[CENTRAL_OFFSET] = 0x00;

        assertMalformedZipBeforeProvider(bytes);
    }

    @Test
    void adapterInvokesProviderForBoundedZipFamilyFraming() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        adapter.convert(request("pptx", framedZip()));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterInvokesProviderForBoundedZipFramingWithComment() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);
        byte[] base = framedZip();
        byte[] withComment = Arrays.copyOf(base, base.length + 2);
        putUnsignedShort(withComment, EOCD_OFFSET + 20, 2);
        withComment[withComment.length - 2] = 'o';
        withComment[withComment.length - 1] = 'k';

        adapter.convert(request("docx", withComment));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterInvokesProviderForQualifiedLegacyCompoundFileSignature() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        adapter.convert(request("doc", COMPOUND_FILE_HEADER));

        assertEquals(1, providerCalls.get());
    }

    private static void assertMalformedZipBeforeProvider(byte[] bytes) {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request("docx", bytes))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP container framing is invalid", failure.getMessage());
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

    private static OfficeConversionRequest request(String sourceFormat, byte[] sourceBytes) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("945bf4f3-48b6-475b-a253-c316969818e6"),
                9L,
                sourceFormat,
                "policy-v1",
                "trace-source-preflight",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] framedZip() {
        byte[] bytes = new byte[34];
        System.arraycopy(ZIP_LOCAL_HEADER, 0, bytes, 0, ZIP_LOCAL_HEADER.length);
        bytes[CENTRAL_OFFSET] = 0x50;
        bytes[CENTRAL_OFFSET + 1] = 0x4b;
        bytes[CENTRAL_OFFSET + 2] = 0x01;
        bytes[CENTRAL_OFFSET + 3] = 0x02;
        bytes[EOCD_OFFSET] = 0x50;
        bytes[EOCD_OFFSET + 1] = 0x4b;
        bytes[EOCD_OFFSET + 2] = 0x05;
        bytes[EOCD_OFFSET + 3] = 0x06;
        putUnsignedShort(bytes, EOCD_OFFSET + 8, 1);
        putUnsignedShort(bytes, EOCD_OFFSET + 10, 1);
        putUnsignedInt(bytes, EOCD_OFFSET + 12, 4);
        putUnsignedInt(bytes, EOCD_OFFSET + 16, CENTRAL_OFFSET);
        putUnsignedShort(bytes, EOCD_OFFSET + 20, 0);
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
