package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * Resource-boundary regressions for request-bound PDF page-count acceptance.
 */
class OfficeConversionPageLimitTest {

    @Test
    void requestBindsPositiveMaximumPdfPages() {
        OfficeConversionRequest request = requestWithLimits(1_000_000L, 2);

        assertEquals(2, request.maxPdfPages());
        assertEquals(2, request.binding().maxPdfPages());
        assertThrows(IllegalArgumentException.class, () -> requestWithLimits(1_000_000L, 0));
        assertThrows(IllegalArgumentException.class, () -> requestWithLimits(1_000_000L, -1));
    }

    @Test
    void pageLimitChangesImmutableRequestBinding() {
        OfficeConversionRequest twoPages = requestWithLimits(1_000_000L, 2);
        OfficeConversionRequest threePages = requestWithLimits(1_000_000L, 3);

        assertNotEquals(twoPages.binding(), threePages.binding());
    }

    @Test
    void adapterRejectsPdfThatExceedsBoundPageLimit() throws IOException {
        OfficeConversionRequest request = requestWithLimits(1_000_000L, 1);
        byte[] pdf = pdfWithPages(2);
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.PAGE_LIMIT_EXCEEDED, failure.failureCode());
        assertEquals("conversion output exceeds maximum pages", failure.getMessage());
    }

    @Test
    void adapterAcceptsPdfAtExactPageLimit() throws IOException {
        OfficeConversionRequest request = requestWithLimits(1_000_000L, 2);
        byte[] pdf = pdfWithPages(2);
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionResult result = adapter.convert(request);

        assertEquals(2, request.maxPdfPages());
        assertEquals(pdf.length, result.pdfBytes().length);
    }

    private static OfficeConversionRequest requestWithLimits(long maxOutputBytes, int maxPdfPages) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("bd7bd272-61d5-4558-937f-2180d00ec4dd"),
                4L,
                "docx",
                "policy-v1",
                "trace-page-limit",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
                maxOutputBytes,
                maxPdfPages
        );
    }

    private static byte[] pdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
