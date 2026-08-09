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
     * Converts one immutable Office request into verified PDF evidence.
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return verified PDF result with source and adapter provenance
     */
    OfficeConversionResult convert(OfficeConversionRequest request);
}
