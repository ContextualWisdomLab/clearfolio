package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Covers validation and compatibility overloads on Office conversion value objects. */
class OfficeConversionValueCoverageTest {

    private static final UUID JOB_ID = UUID.fromString("2af086d7-5739-4b74-9791-5ed4a899f5e8");
    private static final byte[] SOURCE = "office-source".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = new OfficeConversionRequest(
            "tenant", JOB_ID, 1L, "docx", "adapter", "2", "policy", "trace", SOURCE)
            .sourceSha256();

    @Test
    void requestCompatibilityOverloadsPreserveQualifiedAuthority() {
        OfficeConversionRequest defaultLimits = new OfficeConversionRequest(
                " tenant ", JOB_ID, 1L, " DOCX ", " adapter ", " 2 ", " policy ", " trace ", SOURCE);
        OfficeConversionRequest explicitBytes = new OfficeConversionRequest(
                "tenant", JOB_ID, 1L, "docx", "adapter", "2", "policy", "trace", SOURCE, 4096L);
        OfficeConversionRequest fixtureLimits = new OfficeConversionRequest(
                "tenant", JOB_ID, 1L, "docx", "policy", "trace", SOURCE, 2048L, 7);
        OfficeConversionRequest fixtureBytes = new OfficeConversionRequest(
                "tenant", JOB_ID, 1L, "docx", "policy", "trace", SOURCE, 1024L);
        OfficeConversionRequest fixtureDefaults = new OfficeConversionRequest(
                "tenant", JOB_ID, 1L, "docx", "policy", "trace", SOURCE);

        assertEquals("tenant", defaultLimits.tenantId());
        assertEquals("docx", defaultLimits.sourceFormat());
        assertEquals("adapter", defaultLimits.expectedAdapterId());
        assertEquals("2", defaultLimits.expectedAdapterVersion());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES, defaultLimits.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, defaultLimits.maxPdfPages());
        assertEquals(4096L, explicitBytes.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, explicitBytes.maxPdfPages());
        assertEquals("deterministic-fixture", fixtureLimits.expectedAdapterId());
        assertEquals("1", fixtureLimits.expectedAdapterVersion());
        assertEquals(2048L, fixtureLimits.maxOutputBytes());
        assertEquals(7, fixtureLimits.maxPdfPages());
        assertEquals(1024L, fixtureBytes.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, fixtureBytes.maxPdfPages());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES, fixtureDefaults.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, fixtureDefaults.maxPdfPages());

        byte[] returned = defaultLimits.sourceBytes();
        returned[0] = 'X';
        assertArrayEquals(SOURCE, defaultLimits.sourceBytes());
        assertEquals(defaultLimits.binding().sourceSha256(), defaultLimits.sourceSha256());
    }

    @Test
    void requestRejectsEveryInvalidAuthorityAndLimitBranch() {
        assertRequestInvalid(null, JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid(" ", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", null, 0L, "docx", "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, -1L, "docx", "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, null, "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, " ", "adapter", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", null, "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", " ", "1", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", null, "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", " ", "policy", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", null, "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", " ", "trace", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", null, SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", " ", SOURCE, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", null, 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", new byte[0], 1L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", SOURCE, 0L, 1);
        assertRequestInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", SOURCE, 1L, 0);
    }

    @Test
    void bindingCompatibilityOverloadsAndValidationAreFullyCovered() {
        OfficeConversionRequestBinding qualified = new OfficeConversionRequestBinding(
                " tenant ", JOB_ID, 2L, " XLSX ", " adapter ", " 2 ", " policy ", " trace ", DIGEST);
        OfficeConversionRequestBinding fixtureBytes = new OfficeConversionRequestBinding(
                "tenant", JOB_ID, 2L, "xlsx", "policy", "trace", DIGEST, 512L);
        OfficeConversionRequestBinding fixtureDefaults = new OfficeConversionRequestBinding(
                "tenant", JOB_ID, 2L, "xlsx", "policy", "trace", DIGEST);

        assertEquals("tenant", qualified.tenantId());
        assertEquals("xlsx", qualified.sourceFormat());
        assertEquals("adapter", qualified.expectedAdapterId());
        assertEquals("2", qualified.expectedAdapterVersion());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES, qualified.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, qualified.maxPdfPages());
        assertEquals("deterministic-fixture", fixtureBytes.expectedAdapterId());
        assertEquals("1", fixtureBytes.expectedAdapterVersion());
        assertEquals(512L, fixtureBytes.maxOutputBytes());
        assertEquals(OfficeConversionRequest.DEFAULT_MAX_PDF_PAGES, fixtureDefaults.maxPdfPages());

        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", null, "1", "policy", "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", " ", "1", "policy", "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", null, "policy", "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", " ", "policy", "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", null, "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", " ", "trace", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", null, DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", " ", DIGEST, 1L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", DIGEST, 0L, 1);
        assertBindingInvalid("tenant", JOB_ID, 0L, "docx", "adapter", "1", "policy", "trace", DIGEST, 1L, 0);
    }

    @Test
    void resultAndTypedFailureValidationBranchesRemainFailClosed() {
        OfficeConversionRequestBinding binding = new OfficeConversionRequestBinding(
                "tenant", JOB_ID, 1L, "docx", "adapter", "1", "policy", "trace", DIGEST);
        byte[] pdf = OfficeConversionTestPdf.onePage();
        OfficeConversionResult result = new OfficeConversionResult(" adapter ", " 1 ", DIGEST, binding, pdf);
        byte[] returned = result.pdfBytes();
        returned[0] = 'X';
        assertArrayEquals(pdf, result.pdfBytes());
        assertEquals("adapter", result.adapterId());
        assertEquals("1", result.adapterVersion());

        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult(null, "1", DIGEST, pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult(" ", "1", DIGEST, pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult("adapter", null, DIGEST, pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult("adapter", " ", DIGEST, pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult("adapter", "1", null, pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult("adapter", "1", "bad", pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult(
                "adapter", "1", DIGEST, new OfficeConversionRequestBinding(
                        "tenant", JOB_ID, 1L, "docx", "adapter", "1", "policy", "trace", "0".repeat(64)), pdf));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult("adapter", "1", DIGEST, null));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult(
                "adapter", "1", DIGEST, "not-pdf".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionResult(
                "adapter", "1", DIGEST, new byte[0]));

        OfficeConversionException trimmed = new OfficeConversionException(
                OfficeConversionFailureCode.INVALID_OUTPUT, " diagnostic ");
        assertEquals("diagnostic", trimmed.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionException(null, "message"));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionException(
                OfficeConversionFailureCode.INVALID_OUTPUT, null));
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionException(
                OfficeConversionFailureCode.INVALID_OUTPUT, " "));
    }

    private static void assertRequestInvalid(
            String tenantId,
            UUID jobId,
            long generation,
            String sourceFormat,
            String adapterId,
            String adapterVersion,
            String policyVersion,
            String correlationId,
            byte[] sourceBytes,
            long maxOutputBytes,
            int maxPdfPages) {
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequest(
                tenantId, jobId, generation, sourceFormat, adapterId, adapterVersion,
                policyVersion, correlationId, sourceBytes, maxOutputBytes, maxPdfPages));
    }

    private static void assertBindingInvalid(
            String tenantId,
            UUID jobId,
            long generation,
            String sourceFormat,
            String adapterId,
            String adapterVersion,
            String policyVersion,
            String correlationId,
            String digest,
            long maxOutputBytes,
            int maxPdfPages) {
        assertThrows(IllegalArgumentException.class, () -> new OfficeConversionRequestBinding(
                tenantId, jobId, generation, sourceFormat, adapterId, adapterVersion,
                policyVersion, correlationId, digest, maxOutputBytes, maxPdfPages));
    }
}
