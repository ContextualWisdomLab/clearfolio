package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable, privacy-safe authority record for one conversion execution event.
 *
 * <p>The record deliberately stores only pseudonymous tenant and job
 * fingerprints. It does not contain source filenames, document text, token
 * material, or other request content. A consumer can therefore correlate a
 * lifecycle event to an execution without recovering the original identity.
 *
 * @param eventId immutable event identifier
 * @param tenantFingerprint canonical lowercase SHA-256 fingerprint of the tenant
 * @param jobFingerprint canonical lowercase SHA-256 fingerprint of the job
 * @param generation positive execution generation
 * @param attempt zero-based attempt number within the generation
 * @param eventType controlled lifecycle event type
 * @param reasonCode optional bounded lower-snake-case reason code
 * @param occurredAt event timestamp
 */
public record ConversionExecutionAuditEvent(
        UUID eventId,
        String tenantFingerprint,
        String jobFingerprint,
        long generation,
        int attempt,
        ConversionExecutionEventType eventType,
        String reasonCode,
        Instant occurredAt
) {

    private static final int SHA256_HEX_LENGTH = 64;
    private static final int MAX_REASON_CODE_LENGTH = 64;
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REASON_CODE = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

    /**
     * Validates the immutable authority fields at the trust boundary.
     */
    public ConversionExecutionAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireFingerprint(tenantFingerprint, "tenantFingerprint");
        requireFingerprint(jobFingerprint, "jobFingerprint");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        validateReasonCode(reasonCode);
    }

    /**
     * Creates and validates one conversion execution audit event.
     *
     * @param eventId immutable event identifier
     * @param tenantFingerprint canonical lowercase SHA-256 tenant fingerprint
     * @param jobFingerprint canonical lowercase SHA-256 job fingerprint
     * @param generation positive execution generation
     * @param attempt zero-based attempt number
     * @param eventType controlled lifecycle event type
     * @param reasonCode optional lower-snake-case reason code
     * @param occurredAt event timestamp
     * @return validated immutable event
     */
    public static ConversionExecutionAuditEvent create(
            UUID eventId,
            String tenantFingerprint,
            String jobFingerprint,
            long generation,
            int attempt,
            ConversionExecutionEventType eventType,
            String reasonCode,
            Instant occurredAt
    ) {
        return new ConversionExecutionAuditEvent(
                eventId,
                tenantFingerprint,
                jobFingerprint,
                generation,
                attempt,
                eventType,
                reasonCode,
                occurredAt
        );
    }

    /**
     * Checks whether this event belongs to the supplied pseudonymous job
     * execution generation.
     *
     * @param candidateJobFingerprint pseudonymous job fingerprint to compare
     * @param candidateGeneration execution generation to compare
     * @return true only when both immutable execution coordinates match
     */
    public boolean matchesExecution(String candidateJobFingerprint, long candidateGeneration) {
        return jobFingerprint.equals(candidateJobFingerprint) && generation == candidateGeneration;
    }

    private static void requireFingerprint(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.length() != SHA256_HEX_LENGTH || !SHA256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be lowercase SHA-256 hexadecimal");
        }
    }

    private static void validateReasonCode(String value) {
        if (value == null) {
            return;
        }
        if (value.isEmpty()
                || value.length() > MAX_REASON_CODE_LENGTH
                || !REASON_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("reasonCode must be bounded lower snake case");
        }
    }
}
