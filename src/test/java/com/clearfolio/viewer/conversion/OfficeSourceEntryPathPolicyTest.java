package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * ZIP-entry path policy regressions for Office source candidates.
 *
 * <p>The converter may eventually use a filesystem-backed sandbox internally, so
 * path traversal and platform-absolute entry names must fail before provider
 * invocation. Local-header and central-directory entry names must also agree so
 * different ZIP consumers cannot be given conflicting path metadata. These tests
 * inspect archive metadata and do not extract archive contents.</p>
 */
class OfficeSourceEntryPathPolicyTest {

    @Test
    void adapterRejectsParentTraversalEntryBeforeProviderInvocation() {
        assertUnsafeEntry("../outside.bin");
    }

    @Test
    void adapterRejectsNestedParentTraversalEntryBeforeProviderInvocation() {
        assertUnsafeEntry("word/../../outside.bin");
    }

    @Test
    void adapterRejectsLeadingSlashEntryBeforeProviderInvocation() {
        assertUnsafeEntry("/absolute.bin");
    }

    @Test
    void adapterRejectsBackslashEntryBeforeProviderInvocation() {
        assertUnsafeEntry("word\\..\\outside.bin");
    }

    @Test
    void adapterRejectsNulEntryNameBeforeProviderInvocation() {
        assertUnsafeEntry("word/document.xml\u0000.exe");
    }

    @Test
    void adapterRejectsEmptyPathSegmentBeforeProviderInvocation() {
        assertUnsafeEntry("word//document.xml");
    }

    @Test
    void adapterRejectsDirectoryStyleTrailingSlashBeforeProviderInvocation() {
        assertUnsafeEntry("word/");
    }

    @Test
    void adapterRejectsIntermediateSegmentEndingWithDotBeforeProviderInvocation() {
        assertUnsafeEntry("word./document.xml");
    }

    @Test
    void adapterRejectsFinalSegmentEndingWithDotBeforeProviderInvocation() {
        assertUnsafeEntry("word/document.xml.");
    }

    @Test
    void adapterRejectsLocalHeaderNameThatDiffersFromCentralDirectoryBeforeProviderInvocation() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request(zipWithEntryNames("../outside.bin", "word/document.xml")))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP local header does not match central directory", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAllowsRelativeOfficeStyleEntryName() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        adapter.convert(request(zipWithEntry("word/document.xml")));

        assertEquals(1, providerCalls.get());
    }

    private static void assertUnsafeEntry(String entryName) {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request(zipWithEntry(entryName)))
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("source ZIP entry path is unsafe", failure.getMessage());
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
                UUID.fromString("b715d31f-e26f-451d-86fb-d95fb11e8e63"),
                11L,
                "docx",
                "policy-v1",
                "trace-entry-path-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] zipWithEntry(String entryName) {
        return zipWithEntryNames(entryName, entryName);
    }

    private static byte[] zipWithEntryNames(String localEntryName, String centralEntryName) {
        byte[] localNameBytes = localEntryName.getBytes(StandardCharsets.UTF_8);
        byte[] centralNameBytes = centralEntryName.getBytes(StandardCharsets.UTF_8);
        int localHeaderLength = 30 + localNameBytes.length;
        int centralOffset = localHeaderLength;
        int centralLength = 46 + centralNameBytes.length;
        int eocdOffset = centralOffset + centralLength;
        byte[] bytes = new byte[eocdOffset + 22];

        putUnsignedInt(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 26, localNameBytes.length);
        System.arraycopy(localNameBytes, 0, bytes, 30, localNameBytes.length);

        putUnsignedInt(bytes, centralOffset, 0x02014b50L);
        putUnsignedShort(bytes, centralOffset + 28, centralNameBytes.length);
        putUnsignedInt(bytes, centralOffset + 42, 0L);
        System.arraycopy(centralNameBytes, 0, bytes, centralOffset + 46, centralNameBytes.length);

        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, 1);
        putUnsignedShort(bytes, eocdOffset + 10, 1);
        putUnsignedInt(bytes, eocdOffset + 12, centralLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
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
