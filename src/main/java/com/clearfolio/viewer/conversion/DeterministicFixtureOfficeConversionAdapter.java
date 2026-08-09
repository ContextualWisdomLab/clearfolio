package com.clearfolio.viewer.conversion;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic offline Office conversion adapter backed by exact request fixtures.
 *
 * <p>This adapter is a contract and fidelity test implementation, not a production
 * Office renderer. It returns only pre-registered PDF bytes for the exact immutable
 * request binding and therefore cannot silently accept a stale tenant, job,
 * lifecycle generation, format, policy, correlation identity, or source digest.</p>
 */
public final class DeterministicFixtureOfficeConversionAdapter implements OfficeConversionAdapter {

    private static final String ADAPTER_ID = "deterministic-fixture";
    private static final String ADAPTER_VERSION = "1";

    private final Map<OfficeConversionRequestBinding, byte[]> fixtures;

    /**
     * Creates an immutable fixture adapter from exact request bindings to reference PDFs.
     *
     * <p>Fixture byte arrays are defensively copied so later caller mutation cannot
     * change the reference output accepted by the adapter.</p>
     *
     * @param fixtures exact request bindings mapped to reference PDF bytes
     */
    public DeterministicFixtureOfficeConversionAdapter(
            Map<OfficeConversionRequestBinding, byte[]> fixtures) {
        this.fixtures = fixtures.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().clone()
                ));
    }

    /**
     * Returns the exact reference PDF registered for the request binding.
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return deterministic reference PDF with matching request provenance
     * @throws OfficeConversionException when no exact fixture is registered
     */
    @Override
    public OfficeConversionResult performConversion(OfficeConversionRequest request) {
        byte[] pdfBytes = fixtures.get(request.binding());
        if (pdfBytes == null) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "deterministic fixture not registered for request binding"
            );
        }
        return new OfficeConversionResult(
                ADAPTER_ID,
                ADAPTER_VERSION,
                request.sourceSha256(),
                request.binding(),
                pdfBytes
        );
    }
}
