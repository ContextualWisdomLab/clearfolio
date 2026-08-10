package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Integrity regressions for immutable request identity across converter boundaries.
 */
class OfficeConversionRequestBindingTest {

    @Test
    void convertRejectsStaleGenerationEvenWhenSourceBytesMatch() {
        UUID jobId = UUID.randomUUID();
        OfficeConversionRequest current = request(
                "tenant-a", jobId, 2L, "docx", "policy-v2", "trace-current", "same");
        OfficeConversionRequest stale = request(
                "tenant-a", jobId, 1L, "docx", "policy-v2", "trace-current", "same");
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionAdapter adapter = ignored -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                stale.sourceSha256(),
                stale.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(current)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion result request binding mismatch", failure.getMessage());
    }

    @Test
    void bindingChangesAcrossEveryRequestAuthorityField() {
        UUID jobId = UUID.randomUUID();
        OfficeConversionRequest baseline = request(
                "tenant-a", jobId, 2L, "docx", "policy-v2", "trace-a", "same");
        OfficeConversionRequestBinding binding = baseline.binding();

        assertNotEquals(binding,
                request("tenant-b", jobId, 2L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", UUID.randomUUID(), 2L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", jobId, 3L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", jobId, 2L, "xlsx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", jobId, 2L, "docx", "policy-v3", "trace-a", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-b", "same").binding());
        assertNotEquals(binding,
                request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-a", "different").binding());
    }

    @Test
    void bindingCanonicalizesTextAndRejectsInvalidAuthority() {
        UUID jobId = UUID.randomUUID();
        String digest = request(
                "tenant", jobId, 0L, "docx", "policy", "trace", "source").sourceSha256();
        OfficeConversionRequestBinding binding = new OfficeConversionRequestBinding(
                "  tenant-a  ", jobId, 4L, "  DoCx  ", "  policy-v4  ", "  trace-4  ", digest);

        assertEquals("tenant-a", binding.tenantId());
        assertEquals("docx", binding.sourceFormat());
        assertEquals("policy-v4", binding.policyVersion());
        assertEquals("trace-4", binding.correlationId());
        assertEquals(digest, binding.sourceSha256());

        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding(null, jobId, 0L, "docx", "policy", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding(" ", jobId, 0L, "docx", "policy", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", null, 0L, "docx", "policy", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, -1L, "docx", "policy", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, 0L, " ", "policy", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, 0L, "docx", " ", "trace", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, 0L, "docx", "policy", " ", digest));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, 0L, "docx", "policy", "trace", null));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding("tenant", jobId, 0L, "docx", "policy", "trace", "bad"));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionRequestBinding(
                        "tenant", jobId, 0L, "docx", "policy", "trace", digest.toUpperCase()));
    }

    @Test
    void deterministicFixtureAdapterIsExactAndDefensivelyOwned() {
        UUID jobId = UUID.randomUUID();
        OfficeConversionRequest current = request(
                "tenant-a", jobId, 5L, "docx", "policy-v1", "trace-1", "fixture-source");
        OfficeConversionRequest stale = request(
                "tenant-a", jobId, 4L, "docx", "policy-v1", "trace-1", "fixture-source");
        byte[] pdf = OfficeConversionTestPdf.onePage();
        byte[] canonicalPdf = pdf.clone();
        DeterministicFixtureOfficeConversionAdapter adapter = new DeterministicFixtureOfficeConversionAdapter(
                Map.of(current.binding(), pdf)
        );
        pdf[0] = 'X';

        OfficeConversionResult first = adapter.convert(current);
        OfficeConversionResult second = adapter.convert(current);

        assertArrayEquals(canonicalPdf, first.pdfBytes());
        assertArrayEquals(first.pdfBytes(), second.pdfBytes());
        assertEquals(first.outputSha256(), second.outputSha256());
        assertEquals(current.binding(), first.requestBinding());
        assertEquals("deterministic-fixture", first.adapterId());
        assertEquals("1", first.adapterVersion());

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(stale)
        );
        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("deterministic fixture not registered for request binding", failure.getMessage());
    }

    private static OfficeConversionRequest request(
            String tenantId,
            UUID jobId,
            long generation,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            String sourceText) {
        return new OfficeConversionRequest(
                tenantId,
                jobId,
                generation,
                sourceFormat,
                policyVersion,
                correlationId,
                OfficeConversionTestSource.forFormat(sourceFormat, sourceText)
        );
    }
}
