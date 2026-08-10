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
 * Verifies that the OpenDocument manifest advertises the supported ODF package version.
 */
class OfficeOdfManifestVersionPolicyTest {

    private static final String MANIFEST_NAMESPACE =
            "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";

    @Test
    void adapterRejectsManifestVersionOutsideSupportedOdfVersion() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(odfPackage("1.3")))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF manifest version is not allowed", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAcceptsManifestVersionFourteen() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();

        countingAdapter(providerCalls).convert(request(odfPackage("1.4")));

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
                UUID.fromString("931b7157-7840-4435-b65b-0d01fae5b141"),
                14L,
                "odt",
                "policy-v1",
                "trace-odf-manifest-version",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] odfPackage(String version) throws IOException {
        byte[] manifest = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest xmlns:manifest=\""
                + MANIFEST_NAMESPACE
                + "\" manifest:version=\""
                + version
                + "\"/>").getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            CRC32 crc32 = new CRC32();
            crc32.update(manifest);
            ZipEntry entry = new ZipEntry("META-INF/manifest.xml");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(manifest.length);
            entry.setCompressedSize(manifest.length);
            entry.setCrc(crc32.getValue());
            zip.putNextEntry(entry);
            zip.write(manifest);
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
