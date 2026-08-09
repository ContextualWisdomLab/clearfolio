package com.clearfolio.viewer.conversion;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable identity tuple that binds converter output to one exact Office request.
 *
 * <p>The binding includes every request authority field that may distinguish a
 * valid conversion generation even when two jobs carry byte-identical source
 * documents. Equality therefore acts as the stale-generation and cross-request
 * acceptance boundary after a provider returns candidate output.</p>
 *
 * @param tenantId canonical tenant identifier
 * @param jobId immutable conversion job identifier
 * @param jobGeneration lifecycle generation used for stale-work fencing
 * @param sourceFormat canonical lowercase source format
 * @param policyVersion conversion-policy version applied to the request
 * @param correlationId controlled request correlation identifier
 * @param sourceSha256 lowercase SHA-256 digest of the immutable source bytes
 */
public record OfficeConversionRequestBinding(
        String tenantId,
        UUID jobId,
        long jobGeneration,
        String sourceFormat,
        String policyVersion,
        String correlationId,
        String sourceSha256
) {

    /**
     * Validates and canonicalizes the complete immutable request identity.
     *
     * @throws IllegalArgumentException when any authority field is invalid
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
        policyVersion = requireText(policyVersion, "policyVersion");
        correlationId = requireText(correlationId, "correlationId");
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be lowercase SHA-256 hex");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
