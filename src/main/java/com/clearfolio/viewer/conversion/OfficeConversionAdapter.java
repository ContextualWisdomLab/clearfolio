package com.clearfolio.viewer.conversion;

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
     * present and bound to the exact source digest supplied to the provider.
     *
     * <p>This method is the public conversion authority. Implementations supply
     * only {@link #performConversion(OfficeConversionRequest)}; callers cannot
     * accidentally accept a result for a different source document.</p>
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return verified PDF result with source and adapter provenance
     * @throws OfficeConversionException when the provider returns no result or
     *         provenance for a different source document
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
        return result;
    }

    /**
     * Performs provider-specific conversion before Clearfolio validates result provenance.
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return provider result, which the default conversion authority validates
     */
    OfficeConversionResult performConversion(OfficeConversionRequest request);
}
