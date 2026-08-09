package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Source-container regressions for the Office adapter trust boundary.
 *
 * <p>These tests deliberately cover only the common pre-conversion authority:
 * candidate format qualification and declared-format/container-signature
 * agreement. They do not treat a matching ZIP or compound-file signature as a
 * complete safety or fidelity qualification.</p>
 */
class OfficeSourceContainerPreflightTest {

    private static final byte[] ZIP_LOCAL_HEADER = new byte[] {
            0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00
    };
    private static final byte[] COMPOUND_FILE_HEADER = new byte[] {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

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
                () -> adapter.convert(request("xls", ZIP_LOCAL_HEADER))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source container signature does not match declared format", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterInvokesProviderForQualifiedZipFamilySignature() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        adapter.convert(request("pptx", ZIP_LOCAL_HEADER));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterInvokesProviderForQualifiedLegacyCompoundFileSignature() {
        AtomicInteger providerCalls = new AtomicInteger();
        OfficeConversionAdapter adapter = countingAdapter(providerCalls);

        adapter.convert(request("doc", COMPOUND_FILE_HEADER));

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
}
