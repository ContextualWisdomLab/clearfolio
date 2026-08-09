package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Security and integrity regressions for adapter result provenance.
 */
class OfficeConversionAdapterProvenanceTest {

    @Test
    void convertRejectsResultBoundToDifferentSourceDigest() {
        OfficeConversionRequest request = request("source-a");
        OfficeConversionRequest differentSource = request("source-b");
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionAdapter adapter = ignored -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                differentSource.sourceSha256(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion result source digest mismatch", failure.getMessage());
    }

    @Test
    void convertRejectsMissingAdapterResult() {
        OfficeConversionRequest request = request("source-a");
        OfficeConversionAdapter adapter = ignored -> null;

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion adapter returned no result", failure.getMessage());
    }

    private static OfficeConversionRequest request(String sourceText) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.randomUUID(),
                1L,
                "docx",
                "policy-v1",
                "trace-1",
                OfficeConversionTestSource.zipPackage(sourceText)
        );
    }
}
