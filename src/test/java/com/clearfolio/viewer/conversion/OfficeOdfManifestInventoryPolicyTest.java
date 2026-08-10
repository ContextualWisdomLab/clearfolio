package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the ODF manifest enumerates each ordinary package file exactly once.
 */
class OfficeOdfManifestInventoryPolicyTest {

    private static final String MANIFEST_NAMESPACE =
            "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";

    @Test
    void adapterRejectsOrdinaryPackageFileMissingFromManifest() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfPackage(false);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF manifest does not match package file inventory", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAcceptsOrdinaryPackageFileEnumeratedExactlyOnce() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfPackage(true);

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
                UUID.fromString("41bcd923-780f-4f7a-b142-e00fed5ce05e"),
                13L,
                "odt",
                "policy-v1",
                "trace-odf-manifest-inventory",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] odfPackage(boolean listContentXml) throws IOException {
        String fileEntry = listContentXml
                ? "<manifest:file-entry manifest:full-path=\"content.xml\" "
                        + "manifest:media-type=\"text/xml\"/>"
                : "<manifest:file-entry manifest:full-path=\"Pictures/\" "
                        + "manifest:media-type=\"\"/>";
        byte[] manifest = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest xmlns:manifest=\""
                + MANIFEST_NAMESPACE
                + "\" manifest:version=\"1.4\">"
                + fileEntry
                + "</manifest:manifest>").getBytes(StandardCharsets.UTF_8);
        byte[] content = "<office:document-content/>".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeStored(zip, "META-INF/manifest.xml", manifest);
            writeStored(zip, "content.xml", content);
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
}
