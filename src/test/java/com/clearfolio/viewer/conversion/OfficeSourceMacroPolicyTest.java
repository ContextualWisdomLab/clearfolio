package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Active-content regressions for ZIP-family Office conversion candidates.
 */
class OfficeSourceMacroPolicyTest {

    @Test
    void adapterRejectsVbaProjectBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(docxWithVbaProject()))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source Office package contains prohibited active content", failure.getMessage());
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
                UUID.fromString("9ac37475-7937-429c-81d0-3859f1fa0491"),
                13L,
                "docx",
                "policy-v1",
                "trace-macro-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] docxWithVbaProject() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/vbaProject.bin"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
