package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable, privacy-safe authority record for one conversion execution event.
 *
 * <p>The record deliberately stores only versioned keyed audit pseudonyms for
 * tenant and job authority. It rejects unkeyed SHA-256 digests because common
 * tenant identifiers can be recovered through practical dictionary attacks.
 * The pseudonyms are expected to come from a domain-separated keyed HMAC
 * boundary such as {@code AuditPseudonymizer}; the record itself never receives
 * raw tenant, subject, filename, token, document-content, or exception text.</p>
 *
 * @param eventId immutable event identifier
 * @param tenantFingerprint versioned keyed audit pseudonym for the tenant
 * @param jobFingerprint versioned keyed audit pseudonym for the job
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

    private static final int FINGERPRINT_HEX_LENGTH = 32;
    private static final int MAX_KEY_VERSION_LENGTH = 32;
    private static final int MAX_REASON_CODE_LENGTH = 64;
    private static final Pattern FINGERPRINT_HEX = Pattern.compile("[0-9a-f]{32}");
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
     * @param tenantFingerprint versioned keyed tenant audit pseudonym
     * @param jobFingerprint versioned keyed job audit pseudonym
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
     * @param candidateJobFingerprint versioned keyed job pseudonym to compare
     * @param candidateGeneration execution generation to compare
     * @return true only when both immutable execution coordinates match
     */
    public boolean matchesExecution(String candidateJobFingerprint, long candidateGeneration) {
        return jobFingerprint.equals(candidateJobFingerprint) && generation == candidateGeneration;
    }

    private static void requireFingerprint(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            throw invalidFingerprint(fieldName);
        }

        String keyVersion = value.substring(0, separator);
        String digest = value.substring(separator + 1);
        if (keyVersion.length() > MAX_KEY_VERSION_LENGTH
                || digest.length() != FINGERPRINT_HEX_LENGTH
                || !FINGERPRINT_HEX.matcher(digest).matches()) {
            throw invalidFingerprint(fieldName);
        }
        for (int index = 0; index < keyVersion.length(); index++) {
            char character = keyVersion.charAt(index);
            boolean safe = Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '_'
                    || character == '-';
            if (!safe) {
                throw invalidFingerprint(fieldName);
            }
        }
    }

    private static IllegalArgumentException invalidFingerprint(String fieldName) {
        return new IllegalArgumentException(
                fieldName + " must be a versioned keyed audit pseudonym"
        );
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
