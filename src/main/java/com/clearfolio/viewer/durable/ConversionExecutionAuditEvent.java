package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable privacy-safe audit evidence for a conversion execution lifecycle event.
 *
 * <p>The event stores only canonical pseudonymous tenant/job fingerprints plus
 * bounded lifecycle authority. It deliberately excludes raw tenant, subject,
 * filename, document, token, exception, stack-trace, and free-form message data.</p>
 */
public final class ConversionExecutionAuditEvent {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REASON_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final UUID eventId;
    private final String tenantFingerprint;
    private final String jobFingerprint;
    private final long generation;
    private final int attempt;
    private final ConversionExecutionEventType eventType;
    private final String reasonCode;
    private final Instant occurredAt;

    private ConversionExecutionAuditEvent(
            UUID eventId,
            String tenantFingerprint,
            String jobFingerprint,
            long generation,
            int attempt,
            ConversionExecutionEventType eventType,
            String reasonCode,
            Instant occurredAt) {
        this.eventId = eventId;
        this.tenantFingerprint = tenantFingerprint;
        this.jobFingerprint = jobFingerprint;
        this.generation = generation;
        this.attempt = attempt;
        this.eventType = eventType;
        this.reasonCode = reasonCode;
        this.occurredAt = occurredAt;
    }

    /**
     * Creates one immutable execution-audit event.
     *
     * @param eventId unique audit-event identifier
     * @param tenantFingerprint canonical lowercase SHA-256 tenant fingerprint
     * @param jobFingerprint canonical lowercase SHA-256 conversion-job fingerprint
     * @param generation positive lifecycle generation
     * @param attempt non-negative attempt number; zero is valid before a worker claim
     * @param eventType controlled execution lifecycle event type
     * @param reasonCode optional lowercase low-cardinality reason code
     * @param occurredAt persisted event timestamp
     * @return validated immutable audit event
     * @throws NullPointerException when required identity, type, or timestamp is null
     * @throws IllegalArgumentException when fingerprint, generation, attempt, or reason code is invalid
     */
    public static ConversionExecutionAuditEvent create(
            UUID eventId,
            String tenantFingerprint,
            String jobFingerprint,
            long generation,
            int attempt,
            ConversionExecutionEventType eventType,
            String reasonCode,
            Instant occurredAt) {
        UUID requiredEventId = Objects.requireNonNull(eventId, "eventId");
        String requiredTenantFingerprint = requireFingerprint("tenantFingerprint", tenantFingerprint);
        String requiredJobFingerprint = requireFingerprint("jobFingerprint", jobFingerprint);
        ConversionExecutionEventType requiredEventType = Objects.requireNonNull(eventType, "eventType");
        Instant requiredOccurredAt = Objects.requireNonNull(occurredAt, "occurredAt");

        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if (reasonCode != null && !REASON_CODE.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("reasonCode must be lowercase snake_case and at most 64 characters");
        }

        return new ConversionExecutionAuditEvent(
                requiredEventId,
                requiredTenantFingerprint,
                requiredJobFingerprint,
                generation,
                attempt,
                requiredEventType,
                reasonCode,
                requiredOccurredAt
        );
    }

    /**
     * Checks whether a candidate pseudonymous job identity and generation match this event.
     *
     * @param candidateJobFingerprint candidate lowercase SHA-256 job fingerprint
     * @param candidateGeneration candidate lifecycle generation
     * @return true only for an exact non-null fingerprint and generation match
     */
    public boolean matchesExecution(String candidateJobFingerprint, long candidateGeneration) {
        return jobFingerprint.equals(candidateJobFingerprint) && generation == candidateGeneration;
    }

    /**
     * Returns the unique audit-event identifier.
     *
     * @return event identifier
     */
    public UUID eventId() {
        return eventId;
    }

    /**
     * Returns the canonical pseudonymous tenant fingerprint.
     *
     * @return lowercase SHA-256 tenant fingerprint
     */
    public String tenantFingerprint() {
        return tenantFingerprint;
    }

    /**
     * Returns the canonical pseudonymous conversion-job fingerprint.
     *
     * @return lowercase SHA-256 job fingerprint
     */
    public String jobFingerprint() {
        return jobFingerprint;
    }

    /**
     * Returns the lifecycle generation represented by this event.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the attempt number represented by this event.
     *
     * @return non-negative attempt number
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the controlled execution lifecycle event type.
     *
     * @return event type
     */
    public ConversionExecutionEventType eventType() {
        return eventType;
    }

    /**
     * Returns the optional controlled low-cardinality reason code.
     *
     * @return reason code, or null when no reason detail is required
     */
    public String reasonCode() {
        return reasonCode;
    }

    /**
     * Returns the persisted timestamp for the event.
     *
     * @return event timestamp
     */
    public Instant occurredAt() {
        return occurredAt;
    }

    private static String requireFingerprint(String name, String value) {
        String requiredValue = Objects.requireNonNull(value, name);
        if (!SHA256_HEX.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(name + " must be canonical lowercase SHA-256 hex");
        }
        return requiredValue;
    }
}
