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
 * source format, qualified adapter identity, policy, correlation identity, and
 * bounded PDF publication limits before any converter implementation can
 * process them. Source bytes are defensively copied at construction and on
 * access so callers cannot mutate the digest-bound payload after validation.</p>
 *
 * @param tenantId tenant that owns the conversion request
 * @param jobId immutable conversion job identifier
 * @param jobGeneration immutable lifecycle generation for stale-work fencing
 * @param sourceFormat normalized source format such as {@code docx}
 * @param expectedAdapterId qualified adapter implementation identifier
 * @param expectedAdapterVersion exact qualified adapter/runtime version
 * @param policyVersion conversion and active-content policy version
 * @param correlationId request correlation identifier used for controlled tracing
 * @param sourceBytes untrusted source bytes, defensively copied
 * @param maxOutputBytes positive maximum PDF bytes accepted for publication
 * @param maxPdfPages positive maximum PDF pages accepted for publication
 */
public record OfficeConversionRequest(
        String tenantId,
        UUID jobId,
        long jobGeneration,
        String sourceFormat,
        String expectedAdapterId,
        String expectedAdapterVersion,
        String policyVersion,
        String correlationId,
        byte[] sourceBytes,
        long maxOutputBytes,
        int maxPdfPages
) {

    /** Default compatibility byte ceiling for contract callers without a policy-specific limit. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 64L * 1024L * 1024L;

    /** Default compatibility page ceiling for contract callers without a policy-specific limit. */
    public static final int DEFAULT_MAX_PDF_PAGES = 1_000;

    private static final String CONTRACT_FIXTURE_ADAPTER_ID = "deterministic-fixture";
    private static final String CONTRACT_FIXTURE_ADAPTER_VERSION = "1";

    /**
     * Creates a qualified-adapter request using bounded compatibility publication limits.
     *
     * <p>Production integration should use the canonical constructor when policy
     * supplies explicit byte or page ceilings. The exact adapter id and version
     * remain mandatory authority fields.</p>
     *
     * @param tenantId tenant that owns the conversion request
     * @param jobId immutable conversion job identifier
     * @param jobGeneration immutable lifecycle generation
     * @param sourceFormat normalized source format
     * @param expectedAdapterId qualified adapter identifier
     * @param expectedAdapterVersion exact qualified adapter/runtime version
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceBytes untrusted source bytes
     */
    public OfficeConversionRequest(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String expectedAdapterId,
            String expectedAdapterVersion,
            String policyVersion,
            String correlationId,
            byte[] sourceBytes) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                expectedAdapterId,
                expectedAdapterVersion,
                policyVersion,
                correlationId,
                sourceBytes,
                DEFAULT_MAX_OUTPUT_BYTES,
                DEFAULT_MAX_PDF_PAGES
        );
    }

    /**
     * Creates a package-local deterministic-fixture request with explicit publication limits.
     *
     * <p>This compatibility overload is deliberately non-public and bound to the
     * deterministic fixture adapter. A production sidecar or remote-service
     * integration must use the canonical public constructor and supply qualified
     * adapter identity explicitly.</p>
     *
     * @param tenantId tenant that owns the conversion request
     * @param jobId immutable conversion job identifier
     * @param jobGeneration immutable lifecycle generation
     * @param sourceFormat normalized source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceBytes untrusted source bytes
     * @param maxOutputBytes positive maximum PDF bytes accepted for publication
     * @param maxPdfPages positive maximum PDF pages accepted for publication
     */
    OfficeConversionRequest(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            byte[] sourceBytes,
            long maxOutputBytes,
            int maxPdfPages) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                CONTRACT_FIXTURE_ADAPTER_ID,
                CONTRACT_FIXTURE_ADAPTER_VERSION,
                policyVersion,
                correlationId,
                sourceBytes,
                maxOutputBytes,
                maxPdfPages
        );
    }

    /**
     * Creates a package-local deterministic-fixture request with an explicit byte ceiling.
     *
     * @param tenantId tenant that owns the conversion request
     * @param jobId immutable conversion job identifier
     * @param jobGeneration immutable lifecycle generation
     * @param sourceFormat normalized source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceBytes untrusted source bytes
     * @param maxOutputBytes positive maximum PDF bytes accepted for publication
     */
    OfficeConversionRequest(
            String tenantId,
            UUID jobId,
            long jobGeneration,
            String sourceFormat,
            String policyVersion,
            String correlationId,
            byte[] sourceBytes,
            long maxOutputBytes) {
        this(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                policyVersion,
                correlationId,
                sourceBytes,
                maxOutputBytes,
                DEFAULT_MAX_PDF_PAGES
        );
    }

    /**
     * Creates a package-local deterministic-fixture request using bounded compatibility limits.
     *
     * @param tenantId tenant that owns the conversion request
     * @param jobId immutable conversion job identifier
     * @param jobGeneration immutable lifecycle generation
     * @param sourceFormat normalized source format
     * @param policyVersion conversion-policy version
     * @param correlationId controlled correlation identifier
     * @param sourceBytes untrusted source bytes
     */
    OfficeConversionRequest(
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
                DEFAULT_MAX_OUTPUT_BYTES,
                DEFAULT_MAX_PDF_PAGES
        );
    }

    /**
     * Validates immutable conversion identity, qualified adapter identity,
     * publication limits, and copies source bytes.
     *
     * @throws IllegalArgumentException when required identity, source bytes, or limits are invalid
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
        expectedAdapterId = requireText(expectedAdapterId, "expectedAdapterId");
        expectedAdapterVersion = requireText(expectedAdapterVersion, "expectedAdapterVersion");
        policyVersion = requireText(policyVersion, "policyVersion");
        correlationId = requireText(correlationId, "correlationId");
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("sourceBytes must not be empty");
        }
        if (maxOutputBytes <= 0L) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        if (maxPdfPages <= 0) {
            throw new IllegalArgumentException("maxPdfPages must be positive");
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
     * @return request binding containing identity, generation, adapter, policy,
     *         publication limits, and source digest
     */
    public OfficeConversionRequestBinding binding() {
        return new OfficeConversionRequestBinding(
                tenantId,
                jobId,
                jobGeneration,
                sourceFormat,
                expectedAdapterId,
                expectedAdapterVersion,
                policyVersion,
                correlationId,
                sourceSha256(),
                maxOutputBytes,
                maxPdfPages
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
