package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the provider-neutral Office conversion boundary.
 */
class OfficeConversionAdapterContractTest {

    @Test
    void requestDefensivelyCopiesSourceBytesAndBindsImmutableIdentity() {
        byte[] source = "office-source".getBytes(StandardCharsets.UTF_8);
        UUID jobId = UUID.randomUUID();

        OfficeConversionRequest request = new OfficeConversionRequest(
                "tenant-a",
                jobId,
                7L,
                "docx",
                "policy-v3",
                "trace-123",
                source
        );

        String expectedDigest = request.sourceSha256();
        source[0] = 'X';
        byte[] exposed = request.sourceBytes();
        exposed[1] = 'Y';

        assertEquals("tenant-a", request.tenantId());
        assertEquals(jobId, request.jobId());
        assertEquals(7L, request.jobGeneration());
        assertEquals("docx", request.sourceFormat());
        assertEquals("policy-v3", request.policyVersion());
        assertEquals("trace-123", request.correlationId());
        assertArrayEquals("office-source".getBytes(StandardCharsets.UTF_8), request.sourceBytes());
        assertEquals(expectedDigest, request.sourceSha256());
        assertEquals(64, expectedDigest.length());
    }

    @Test
    void requestCanonicalizesSourceFormatBeforeAdapterRouting() {
        OfficeConversionRequest request = new OfficeConversionRequest(
                "tenant-a",
                UUID.randomUUID(),
                1L,
                "  DoCx  ",
                "policy-v1",
                "trace-1",
                "source".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("docx", request.sourceFormat());
    }

    @Test
    void requestRejectsMissingIdentityAndEmptySource() {
        byte[] source = "x".getBytes(StandardCharsets.UTF_8);
        UUID jobId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                null, jobId, 0L, "docx", "policy", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                " ", jobId, 0L, "docx", "policy", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", null, 0L, "docx", "policy", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, -1L, "docx", "policy", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, 0L, " ", "policy", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, 0L, "docx", " ", "trace", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, 0L, "docx", "policy", " ", source));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, 0L, "docx", "policy", "trace", null));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                "tenant", jobId, 0L, "docx", "policy", "trace", new byte[0]));
    }

    @Test
    void resultDefensivelyCopiesVerifiedPdfAndCarriesProvenance() {
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        OfficeConversionResult result = new OfficeConversionResult(
                "fixture-adapter",
                "1.0.0",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                pdf
        );

        String outputDigest = result.outputSha256();
        pdf[0] = 'X';
        byte[] exposed = result.pdfBytes();
        exposed[1] = 'Y';

        assertEquals("fixture-adapter", result.adapterId());
        assertEquals("1.0.0", result.adapterVersion());
        assertEquals(64, result.sourceSha256().length());
        assertArrayEquals("%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII), result.pdfBytes());
        assertEquals(outputDigest, result.outputSha256());
        assertEquals(64, outputDigest.length());
    }

    @Test
    void resultRejectsInvalidProvenanceAndNonPdfOutput() {
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        byte[] notPdf = "not-pdf".getBytes(StandardCharsets.US_ASCII);
        String digest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult(null, "1", digest, pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult(" ", "1", digest, pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", " ", digest, pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", null, pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", "bad", pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", digest.toUpperCase(), pdf));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", digest, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", digest, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult("adapter", "1", digest, notPdf));
    }

    @Test
    void failureCodesExposeExplicitRetryPolicy() {
        assertFalse(OfficeConversionFailureCode.UNSUPPORTED_FORMAT.isRetryable());
        assertFalse(OfficeConversionFailureCode.POLICY_DENIED.isRetryable());
        assertFalse(OfficeConversionFailureCode.PASSWORD_PROTECTED.isRetryable());
        assertFalse(OfficeConversionFailureCode.MALFORMED_INPUT.isRetryable());
        assertFalse(OfficeConversionFailureCode.CANCELLED.isRetryable());
        assertFalse(OfficeConversionFailureCode.INVALID_OUTPUT.isRetryable());
        assertTrue(OfficeConversionFailureCode.ENGINE_UNAVAILABLE.isRetryable());
        assertTrue(OfficeConversionFailureCode.TIMEOUT.isRetryable());
        assertTrue(OfficeConversionFailureCode.ENGINE_CRASH.isRetryable());
    }

    @Test
    void adapterContractCanReturnDeterministicFixtureEvidence() {
        byte[] source = "fixture-docx".getBytes(StandardCharsets.UTF_8);
        OfficeConversionRequest request = new OfficeConversionRequest(
                "tenant-a", UUID.randomUUID(), 1L, "docx", "policy-v1", "trace-1", source);
        byte[] pdf = "%PDF-1.7\nreference".getBytes(StandardCharsets.US_ASCII);

        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                pdf
        );

        OfficeConversionResult result = adapter.convert(request);

        assertEquals(request.sourceSha256(), result.sourceSha256());
        assertArrayEquals(pdf, result.pdfBytes());
    }
}
