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
 * an output-publication size ceiling before any converter implementation can
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
        long maxOutputBytes
) {

    /** Default compatibility ceiling for contract callers that have not supplied a policy-specific limit. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 64L * 1024L * 1024L;

    private static final String CONTRACT_FIXTURE_ADAPTER_ID = "deterministic-fixture";
    private static final String CONTRACT_FIXTURE_ADAPTER_VERSION = "1";

    /**
     * Creates a qualified-adapter request using the bounded compatibility output ceiling.
     *
     * <p>Production integration should prefer this overload when the output
     * ceiling is inherited from the currently qualified policy. The exact
     * adapter id and version remain mandatory authority fields.</p>
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
                DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    /**
     * Creates a deterministic-fixture contract request with an explicit output ceiling.
     *
     * <p>This compatibility overload is deliberately bound to the deterministic
     * fixture adapter. A production sidecar or remote-service integration must
     * use an overload that supplies its qualified adapter id and exact version;
     * it cannot silently inherit this fixture identity.</p>
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
    public OfficeConversionRequest(
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
                CONTRACT_FIXTURE_ADAPTER_ID,
                CONTRACT_FIXTURE_ADAPTER_VERSION,
                policyVersion,
                correlationId,
                sourceBytes,
                maxOutputBytes
        );
    }

    /**
     * Creates a deterministic-fixture contract request using the bounded compatibility output ceiling.
     *
     * <p>This overload exists for the offline contract fixture only. Production
     * adapter integration must name the qualified adapter id and exact runtime
     * version explicitly so provider provenance cannot float independently of
     * the immutable request binding.</p>
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
                CONTRACT_FIXTURE_ADAPTER_ID,
                CONTRACT_FIXTURE_ADAPTER_VERSION,
                policyVersion,
                correlationId,
                sourceBytes,
                DEFAULT_MAX_OUTPUT_BYTES
        );
    }

    /**
     * Validates immutable conversion identity, qualified adapter identity, the
     * publication limit, and copies source bytes.
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
     *         output limit, and source digest
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
