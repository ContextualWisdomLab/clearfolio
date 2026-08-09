package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Resource-boundary regressions for Office conversion output acceptance.
 */
class OfficeConversionOutputLimitTest {

    @Test
    void requestBindsPositiveMaximumOutputBytes() {
        OfficeConversionRequest request = requestWithLimit(20L);

        assertEquals(20L, request.maxOutputBytes());
        assertEquals(20L, request.binding().maxOutputBytes());
        assertThrows(IllegalArgumentException.class, () -> requestWithLimit(0L));
        assertThrows(IllegalArgumentException.class, () -> requestWithLimit(-1L));
    }

    @Test
    void outputLimitChangesImmutableRequestBinding() {
        OfficeConversionRequest small = requestWithLimit(20L);
        OfficeConversionRequest large = new OfficeConversionRequest(
                small.tenantId(),
                small.jobId(),
                small.jobGeneration(),
                small.sourceFormat(),
                small.policyVersion(),
                small.correlationId(),
                small.sourceBytes(),
                21L
        );

        org.junit.jupiter.api.Assertions.assertNotEquals(small.binding(), large.binding());
    }

    @Test
    void adapterRejectsPdfThatExceedsBoundOutputLimit() {
        OfficeConversionRequest request = requestWithLimit(8L);
        byte[] pdf = "%PDF-1.7\nreference".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.OUTPUT_LIMIT_EXCEEDED, failure.failureCode());
        assertEquals("conversion output exceeds maximum bytes", failure.getMessage());
    }

    @Test
    void adapterAcceptsParseablePdfAtExactOutputLimit() {
        byte[] pdf = OfficeConversionTestPdf.onePage();
        OfficeConversionRequest request = requestWithLimit(pdf.length);
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionResult result = adapter.convert(request);

        assertEquals(pdf.length, result.pdfBytes().length);
    }

    private static OfficeConversionRequest requestWithLimit(long maxOutputBytes) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("1eaf3d24-f238-4a14-a909-47c20d264282"),
                3L,
                "docx",
                "policy-v1",
                "trace-output-limit",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
                maxOutputBytes
        );
    }
}
