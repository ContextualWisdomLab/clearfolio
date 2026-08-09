package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * Output-structure regressions for converter-produced PDF candidates.
 */
class OfficeConversionPdfValidationTest {

    @Test
    void adapterRejectsTruncatedMagicOnlyPdf() {
        OfficeConversionRequest request = request();
        byte[] truncated = "%PDF-1.7\nnot-a-complete-document".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                truncated
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion output is not a valid PDF", failure.getMessage());
    }

    @Test
    void adapterAcceptsParseablePdf() throws IOException {
        OfficeConversionRequest request = request();
        byte[] pdf = onePagePdf();
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionResult result = adapter.convert(request);

        assertArrayEquals(pdf, result.pdfBytes());
    }

    private static OfficeConversionRequest request() {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("d031f25a-8d92-4c9d-a89f-362e0324c8ef"),
                8L,
                "docx",
                "policy-v1",
                "trace-pdf-validation",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
                OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
