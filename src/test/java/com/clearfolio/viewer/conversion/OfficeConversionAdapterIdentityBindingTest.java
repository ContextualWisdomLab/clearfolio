package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Integrity regressions for binding Office output to the qualified adapter identity.
 */
class OfficeConversionAdapterIdentityBindingTest {

    @Test
    void requestBindingIncludesExpectedAdapterIdentity() {
        OfficeConversionRequest baseline = request("sandboxed-office-sidecar", "24.8.5");
        OfficeConversionRequest otherAdapter = request("remote-office-service", "24.8.5");
        OfficeConversionRequest otherVersion = request("sandboxed-office-sidecar", "24.8.6");

        assertEquals("sandboxed-office-sidecar", baseline.expectedAdapterId());
        assertEquals("24.8.5", baseline.expectedAdapterVersion());
        assertEquals("sandboxed-office-sidecar", baseline.binding().expectedAdapterId());
        assertEquals("24.8.5", baseline.binding().expectedAdapterVersion());
        assertNotEquals(baseline.binding(), otherAdapter.binding());
        assertNotEquals(baseline.binding(), otherVersion.binding());
    }

    @Test
    void publicRequestConstructorsRequireExplicitQualifiedAdapterIdentity() {
        boolean allPublicConstructorsRequireAdapterIdentity = Arrays.stream(OfficeConversionRequest.class.getConstructors())
                .allMatch(constructor -> constructor.getParameterCount() >= 9);

        assertTrue(
                allPublicConstructorsRequireAdapterIdentity,
                "public conversion requests must not silently bind a fixture/default adapter identity"
        );
    }

    @Test
    void adapterRejectsResultFromUnexpectedAdapterIdentity() {
        OfficeConversionRequest request = request("sandboxed-office-sidecar", "24.8.5");
        byte[] pdf = OfficeConversionTestPdf.onePage();
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "remote-office-service",
                "24.8.5",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion result adapter identity mismatch", failure.getMessage());
    }

    @Test
    void adapterRejectsResultFromUnexpectedAdapterVersion() {
        OfficeConversionRequest request = request("sandboxed-office-sidecar", "24.8.5");
        byte[] pdf = OfficeConversionTestPdf.onePage();
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "sandboxed-office-sidecar",
                "24.8.6",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.INVALID_OUTPUT, failure.failureCode());
        assertEquals("conversion result adapter identity mismatch", failure.getMessage());
    }

    private static OfficeConversionRequest request(String adapterId, String adapterVersion) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("ce0a17f5-cdee-44db-9547-c7ed5e6d2f19"),
                4L,
                "docx",
                adapterId,
                adapterVersion,
                "policy-v3",
                "trace-adapter-binding",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
                OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES
        );
    }
}
