package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Prevents duplicate logical ZIP entry names from reaching an Office provider.
 */
class OfficeSourceDuplicateEntryPolicyTest {

    private static final byte[] FIRST_NAME = "content.xml".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SECOND_NAME = "stylesx.xml".getBytes(StandardCharsets.US_ASCII);

    @Test
    void adapterRejectsDuplicateZipEntryNameBeforeProviderInvocation() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = duplicateNamePackage();

        OfficeConversionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP contains duplicate entry name", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsAsciiCaseEquivalentOpcPartNamesBeforeProviderInvocation() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = caseVariantDuplicateNamePackage();

        OfficeConversionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ZIP contains duplicate entry name", failure.getMessage());
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
                UUID.fromString("516f661f-06a7-49dc-9c14-4f2e4161758c"),
                13L,
                "docx",
                "policy-v1",
                "trace-duplicate-entry-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] duplicateNamePackage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addStoredEmptyEntry(zip, new String(FIRST_NAME, StandardCharsets.US_ASCII));
            addStoredEmptyEntry(zip, new String(SECOND_NAME, StandardCharsets.US_ASCII));
        }
        byte[] bytes = output.toByteArray();
        replaceAll(bytes, SECOND_NAME, FIRST_NAME);
        return bytes;
    }

    private static byte[] caseVariantDuplicateNamePackage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addStoredEmptyEntry(zip, "word/document.xml");
            addStoredEmptyEntry(zip, "WORD/DOCUMENT.XML");
        }
        return output.toByteArray();
    }

    private static void addStoredEmptyEntry(ZipOutputStream zip, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(0L);
        entry.setCompressedSize(0L);
        entry.setCrc(0L);
        zip.putNextEntry(entry);
        zip.closeEntry();
    }

    private static void replaceAll(byte[] bytes, byte[] source, byte[] replacement) {
        if (source.length != replacement.length) {
            throw new IllegalArgumentException("replacement must preserve ZIP filename length");
        }
        for (int offset = 0; offset <= bytes.length - source.length; offset++) {
            if (!matchesAt(bytes, offset, source)) {
                continue;
            }
            System.arraycopy(replacement, 0, bytes, offset, replacement.length);
            offset += source.length - 1;
        }
    }

    private static boolean matchesAt(byte[] bytes, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
