package com.clearfolio.viewer.conversion;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Provider-neutral boundary for sandboxed or remote Office-to-PDF conversion.
 *
 * <p>Implementations own converter-specific transport and process details. The
 * Clearfolio API and job lifecycle depend only on this contract so a sandboxed
 * sidecar, authenticated remote service, or deterministic fixture adapter can
 * be substituted without changing document-delivery authority.</p>
 */
@FunctionalInterface
public interface OfficeConversionAdapter {

    /**
     * Converts one immutable Office request and verifies that the result is
     * present, source-bound, tied to the exact request generation and policy,
     * within the request-bound publication size ceiling, and parseable as PDF.
     *
     * <p>This method is the public conversion authority. Implementations supply
     * only {@link #performConversion(OfficeConversionRequest)}; callers cannot
     * accidentally accept output for a different source, tenant, job, lifecycle
     * generation, format, policy, correlation identity, output-size policy, or
     * a truncated byte sequence that only carries a PDF magic prefix.</p>
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return verified PDF result with source, request, and adapter provenance
     * @throws OfficeConversionException when the provider returns no result,
     *         mismatched provenance, an oversized candidate, or malformed PDF
     */
    default OfficeConversionResult convert(OfficeConversionRequest request) {
        OfficeConversionResult result = performConversion(request);
        if (result == null) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion adapter returned no result"
            );
        }
        if (!request.sourceSha256().equals(result.sourceSha256())) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion result source digest mismatch"
            );
        }
        if (!request.binding().equals(result.requestBinding())) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion result request binding mismatch"
            );
        }

        byte[] pdfBytes = result.pdfBytes();
        if (pdfBytes.length > request.maxOutputBytes()) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.OUTPUT_LIMIT_EXCEEDED,
                    "conversion output exceeds maximum bytes"
            );
        }
        requireParseablePdf(pdfBytes);
        return result;
    }

    /**
     * Performs provider-specific conversion before Clearfolio validates result provenance.
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return provider result, which the default conversion authority validates
     */
    OfficeConversionResult performConversion(OfficeConversionRequest request);

    private static void requireParseablePdf(byte[] pdfBytes) {
        try (PDDocument ignored = Loader.loadPDF(pdfBytes)) {
            // Loading and closing the bounded candidate proves PDFBox can parse its structure.
        } catch (IOException ex) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion output is not a valid PDF"
            );
        }
    }
}
