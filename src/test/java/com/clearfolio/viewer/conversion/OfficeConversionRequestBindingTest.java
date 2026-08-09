package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Integrity regressions for immutable request identity across converter boundaries.
 */
class OfficeConversionRequestBindingTest {

    @Test
    void convertRejectsStaleGenerationEvenWhenSourceBytesMatch() {
        UUID jobId = UUID.randomUUID();
        OfficeConversionRequest current = request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-current", "same");
        OfficeConversionRequest stale = request("tenant-a", jobId, 1L, "docx", "policy-v2", "trace-current", "same");
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionAdapter adapter = ignored -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
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
        OfficeConversionRequest baseline = request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-a", "same");
        OfficeConversionRequestBinding binding = baseline.binding();

        assertNotEquals(binding, request("tenant-b", jobId, 2L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding, request("tenant-a", UUID.randomUUID(), 2L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding, request("tenant-a", jobId, 3L, "docx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding, request("tenant-a", jobId, 2L, "xlsx", "policy-v2", "trace-a", "same").binding());
        assertNotEquals(binding, request("tenant-a", jobId, 2L, "docx", "policy-v3", "trace-a", "same").binding());
        assertNotEquals(binding, request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-b", "same").binding());
        assertNotEquals(binding, request("tenant-a", jobId, 2L, "docx", "policy-v2", "trace-a", "different").binding());
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
                sourceText.getBytes(StandardCharsets.UTF_8)
        );
    }
}
