package com.clearfolio.viewer.conversion;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable identity tuple that binds converter output to one exact Office request.
 *
 * <p>The binding includes every request authority field that may distinguish a
 * valid conversion generation even when two jobs carry byte-identical source
 * documents. Equality therefore acts as the stale-generation, provider-version,
 * and cross-request acceptance boundary after a provider returns candidate output.</p>
 *
 * @param tenantId canonical tenant identifier
 * @param jobId immutable conversion job identifier
 * @param jobGeneration lifecycle generation used for stale-work fencing
 * @param sourceFormat canonical lowercase source format
 * @param expectedAdapterId qualified adapter implementation identifier
 * @param expectedAdapterVersion exact qualified adapter/runtime version
 * @param policyVersion conversion-policy version applied to the request
 * @param correlationId controlled request correlation identifier
 * @param sourceSha256 lowercase SHA-256 digest of the immutable source bytes
 * @param maxOutputBytes positive maximum PDF bytes accepted for publication
 */
public record OfficeConversionRequestBinding(
        String tenantId,
        UUID jobId,
        long jobGeneration,
        String sourceFormat,
        String expectedAdapterId,
        String expectedAdapterVersion,
        String policyVersion,
        String correlationId,
        String sourceSha256,
        long maxOutputBytes
) {

    private static final String CONTRACT_FIXTURE_ADAPTER_ID = "deterministic-fixture";
    private static final String CONTRACT_FIXTURE_ADAPTER_VERSION = "1";

    /**
     * Creates a qualified-adapter binding using the request compatibility output ceiling.
     *
     * @param tenantId canonical tenant identifier
     * @param jobId immutable conversion job identifier
     * @param jobGeneration lifecycle generation
     * @param sourceFormat canonical source format
     * @param expectedAdapterId qualified adapter identifier
     * @param expectedAdapterVersion exact qualified adapter/runtime version
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceSha256 lowercase source digest
     */
    public OfficeConversionRequestBinding(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String expectedAdapterId,
            String expectedAdapterVersion,
            String policyVersion,
            String correlationId,
            String sourceSha256) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                expectedAdapterId,
                expectedAdapterVersion,
                policyVersion,
                correlationId,
                sourceSha256,
                OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    /**
     * Creates a deterministic-fixture contract binding with an explicit output ceiling.
     *
     * <p>This compatibility overload is fail-closed for production adapters: it
     * binds the deterministic fixture id/version. Production sidecar or remote
     * integrations must supply their qualified adapter identity explicitly.</p>
     *
     * @param tenantId canonical tenant identifier
     * @param jobId immutable conversion job identifier
     * @param jobGeneration lifecycle generation
     * @param sourceFormat canonical source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceSha256 lowercase source digest
     * @param maxOutputBytes positive maximum PDF bytes accepted for publication
     */
    public OfficeConversionRequestBinding(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            String sourceSha256,
            long maxOutputBytes) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                CONTRACT_FIXTURE_ADAPTER_ID,
                CONTRACT_FIXTURE_ADAPTER_VERSION,
                policyVersion,
                correlationId,
                sourceSha256,
                maxOutputBytes
        );
    }

    /**
     * Creates a deterministic-fixture contract binding using the request compatibility output ceiling.
     *
     * @param tenantId canonical tenant identifier
     * @param jobId immutable conversion job identifier
     * @param jobGeneration lifecycle generation
     * @param sourceFormat canonical source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceSha256 lowercase source digest
     */
    public OfficeConversionRequestBinding(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            String sourceSha256) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                CONTRACT_FIXTURE_ADAPTER_ID,
                CONTRACT_FIXTURE_ADAPTER_VERSION,
                policyVersion,
                correlationId,
                sourceSha256,
                OfficeConversionRequest.DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    /**
     * Validates and canonicalizes the complete immutable request identity.
     *
     * @throws IllegalArgumentException when any authority field or limit is invalid
     */
    public OfficeConversionRequestBinding {
        tenantId = requireText(tenantId, "tenantId");
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        if (jobGeneration < 0L) {
            throw new IllegalArgumentException("jobGeneration must be non-negative");
        }
        sourceFormat = requireText(sourceFormat, "sourceFormat").toLowerCase(Locale.ROOT);
        expectedAdapterId = requireText(expectedAdapterId, "expectedAdapterId");
        expectedAdapterVersion = requireText(expectedAdapterVersion, "expectedAdapterVersion");
        policyVersion = requireText(policyVersion, "policyVersion");
        correlationId = requireText(correlationId, "correlationId");
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be lowercase SHA-256 hex");
        }
        if (maxOutputBytes <= 0L) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
