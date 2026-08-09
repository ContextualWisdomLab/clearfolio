package com.clearfolio.viewer.conversion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Immutable request passed across the provider-neutral Office conversion boundary.
 *
 * <p>The request binds untrusted document bytes to tenant, job-generation,
 * policy, format, and correlation identity before any converter implementation
 * can process them. Source bytes are defensively copied at construction and on
 * access so callers cannot mutate the digest-bound payload after validation.</p>
 *
 * @param tenantId tenant that owns the conversion request
 * @param jobId immutable conversion job identifier
 * @param jobGeneration immutable lifecycle generation for stale-work fencing
 * @param sourceFormat normalized source format such as {@code docx}
 * @param policyVersion conversion and active-content policy version
 * @param correlationId request correlation identifier used for controlled tracing
 * @param sourceBytes untrusted source bytes, defensively copied
 */
public record OfficeConversionRequest(
        String tenantId,
        UUID jobId,
        long jobGeneration,
        String sourceFormat,
        String policyVersion,
        String correlationId,
        byte[] sourceBytes
) {

    /**
     * Validates immutable conversion identity and copies the untrusted source bytes.
     *
     * @throws IllegalArgumentException when required identity or source bytes are invalid
     */
    public OfficeConversionRequest {
        tenantId = requireText(tenantId, "tenantId");
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        if (jobGeneration < 0L) {
            throw new IllegalArgumentException("jobGeneration must be non-negative");
        }
        sourceFormat = requireText(sourceFormat, "sourceFormat");
        policyVersion = requireText(policyVersion, "policyVersion");
        correlationId = requireText(correlationId, "correlationId");
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("sourceBytes must not be empty");
        }
        sourceBytes = sourceBytes.clone();
    }

    /**
     * Returns a defensive copy of the source bytes.
     *
     * @return copied source bytes
     */
    @Override
    public byte[] sourceBytes() {
        return sourceBytes.clone();
    }

    /**
     * Returns the SHA-256 digest of the immutable source bytes.
     *
     * @return lowercase hexadecimal SHA-256 digest
     */
    public String sourceSha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sourceBytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
