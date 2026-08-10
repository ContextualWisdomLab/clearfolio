package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the OpenDocument META-INF package namespace before provider invocation.
 */
class OfficeOdfMetaInfPolicyTest {

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
                ZipEntry entry = new ZipEntry(entryName);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(0L);
                entry.setCompressedSize(0L);
                entry.setCrc(0L);
                zip.putNextEntry(entry);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
