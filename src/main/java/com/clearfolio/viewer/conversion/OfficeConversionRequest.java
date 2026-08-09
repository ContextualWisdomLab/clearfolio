package com.clearfolio.viewer.conversion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable request passed across the provider-neutral Office conversion boundary.
 *
 * <p>The request binds untrusted document bytes to tenant, job-generation,
 * policy, format, correlation identity, and an output-publication size ceiling
 * before any converter implementation can process them. Source bytes are
 * defensively copied at construction and on access so callers cannot mutate the
 * digest-bound payload after validation.</p>
 *
 * @param tenantId tenant that owns the conversion request
 * @param jobId immutable conversion job identifier
 * @param jobGeneration immutable lifecycle generation for stale-work fencing
 * @param sourceFormat normalized source format such as {@code docx}
 * @param policyVersion conversion and active-content policy version
 * @param correlationId request correlation identifier used for controlled tracing
 * @param sourceBytes untrusted source bytes, defensively copied
 * @param maxOutputBytes positive maximum PDF bytes accepted for publication
 */
public record OfficeConversionRequest(
        String tenantId,
        UUID jobId,
        long jobGeneration,
        String sourceFormat,
        String policyVersion,
        String correlationId,
        byte[] sourceBytes,
        long maxOutputBytes
) {

    /** Default compatibility ceiling for contract callers that have not supplied a policy-specific limit. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 64L * 1024L * 1024L;

    /**
     * Creates a request using the bounded compatibility output ceiling.
     *
     * <p>Production adapter integration should supply the policy-specific output
     * ceiling explicitly. This overload keeps existing contract callers bounded
     * while the provider runtime remains unintegrated.</p>
     *
     * @param tenantId tenant that owns the conversion request
     * @param jobId immutable conversion job identifier
     * @param jobGeneration immutable lifecycle generation
     * @param sourceFormat normalized source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceBytes untrusted source bytes
     */
    public OfficeConversionRequest(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            byte[] sourceBytes) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                policyVersion,
                correlationId,
                sourceBytes,
                DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    /**
     * Validates immutable conversion identity, the publication limit, and copies source bytes.
     *
     * @throws IllegalArgumentException when required identity, source bytes, or limit are invalid
     */
    public OfficeConversionRequest {
        tenantId = requireText(tenantId, "tenantId");
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        if (jobGeneration < 0L) {
            throw new IllegalArgumentException("jobGeneration must be non-negative");
        }
        sourceFormat = normalizeSourceFormat(sourceFormat);
        policyVersion = requireText(policyVersion, "policyVersion");
        correlationId = requireText(correlationId, "correlationId");
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("sourceBytes must not be empty");
        }
        if (maxOutputBytes <= 0L) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
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

    /**
     * Returns the full immutable authority tuple for provider-output validation.
     *
     * @return request binding containing identity, generation, policy, output limit, and source digest
     */
    public OfficeConversionRequestBinding binding() {
        return new OfficeConversionRequestBinding(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                policyVersion,
                correlationId,
                sourceSha256(),
                maxOutputBytes
        );
    }

    private static String normalizeSourceFormat(String value) {
        return requireText(value, "sourceFormat").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
