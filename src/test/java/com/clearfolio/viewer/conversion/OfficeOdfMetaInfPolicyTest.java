package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Enforces the OpenDocument META-INF package namespace before provider invocation.
 */
class OfficeOdfMetaInfPolicyTest {

    private static final byte[] MANIFEST_XML = (
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<manifest:manifest "
                    + "xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" "
                    + "manifest:version=\"1.4\"/>"
    ).getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNATURE_XML = (
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<dsig:document-signatures "
                    + "xmlns:dsig=\"urn:oasis:names:tc:opendocument:xmlns:digitalsignature:1.0\"/>"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void adapterRejectsUnexpectedMetaInfEntryBeforeProviderInvocation() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfPackage("META-INF/manifest.xml", "META-INF/evil.xml");

        OfficeConversionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source ODF META-INF entry is not allowed", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAllowsSignatureNamedMetaInfEntry() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = odfPackage(
                "META-INF/manifest.xml",
                "META-INF/documentsignatures.xml"
        );

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
                UUID.fromString("679a23eb-e2b2-4760-8d0f-df50e60c7158"),
                12L,
                "odt",
                "policy-v1",
                "trace-odf-meta-inf-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] odfPackage(String... entryNames) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String entryName : entryNames) {
                byte[] payload = payloadFor(entryName);
                CRC32 crc32 = new CRC32();
                crc32.update(payload);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(payload.length);
                entry.setCompressedSize(payload.length);
                entry.setCrc(crc32.getValue());
                zip.putNextEntry(entry);
                zip.write(payload);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] payloadFor(String entryName) {
        if ("META-INF/manifest.xml".equals(entryName)) {
            return MANIFEST_XML;
        }
        if (entryName.contains("signatures")) {
            return SIGNATURE_XML;
        }
        return new byte[0];
    }
}
