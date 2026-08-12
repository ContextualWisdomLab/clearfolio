package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable durable evidence for one terminal conversion failure.
 *
 * <p>The record binds dead-letter authority to an exact conversion job generation
 * and attempt. The reason is restricted to a bounded low-cardinality code so a
 * durable adapter does not accidentally promote arbitrary exception text, document
 * content, paths, or tenant-controlled values into long-lived operational evidence.</p>
 */
public final class ConversionDeadLetterRecord {

    private static final Pattern REASON_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<String> REGISTERED_REASON_CODES = Set.of(
            "CONVERTER_TIMEOUT",
            "RETRY_EXHAUSTED"
    );

    private final UUID deadLetterId;
    private final UUID jobId;
    private final long generation;
    private final int attempt;
    private final Instant failedAt;
    private final String reasonCode;

    private ConversionDeadLetterRecord(
            UUID deadLetterId,
            UUID jobId,
            long generation,
            int attempt,
            Instant failedAt,
            String reasonCode) {
        this.deadLetterId = deadLetterId;
        this.jobId = jobId;
        this.generation = generation;
        this.attempt = attempt;
        this.failedAt = failedAt;
        this.reasonCode = reasonCode;
    }

    /**
     * Creates one durable dead-letter record.
     *
     * @param deadLetterId unique identifier for this terminal-failure record
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation that failed
     * @param attempt positive processing attempt that reached terminal failure
     * @param failedAt persisted instant when terminal failure was recorded
     * @param reasonCode registered bounded uppercase low-cardinality failure code
     * @return immutable dead-letter record
     * @throws NullPointerException when a required identifier, timestamp, or reason is null
     * @throws IllegalArgumentException when authority, reason syntax, or reason registration is invalid
     */
    public static ConversionDeadLetterRecord record(
            UUID deadLetterId,
            UUID jobId,
            long generation,
            int attempt,
            Instant failedAt,
            String reasonCode) {
        UUID requiredDeadLetterId = Objects.requireNonNull(deadLetterId, "deadLetterId");
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Instant requiredFailedAt = Objects.requireNonNull(failedAt, "failedAt");
        String requiredReasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (!REASON_CODE_PATTERN.matcher(requiredReasonCode).matches()) {
            throw new IllegalArgumentException("reasonCode must be a bounded uppercase code");
        }
        if (!REGISTERED_REASON_CODES.contains(requiredReasonCode)) {
            throw new IllegalArgumentException("reasonCode must be registered");
        }
        return new ConversionDeadLetterRecord(
                requiredDeadLetterId,
                requiredJobId,
                generation,
                attempt,
                requiredFailedAt,
                requiredReasonCode
        );
    }

    /**
     * Checks whether candidate failure authority exactly matches this dead letter.
     *
     * @param candidateJobId candidate conversion-job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @param candidateAttempt candidate processing attempt
     * @return true only when every authority component exactly matches
     */
    public boolean authorizes(UUID candidateJobId, long candidateGeneration, int candidateAttempt) {
        return jobId.equals(candidateJobId)
                && generation == candidateGeneration
                && attempt == candidateAttempt;
    }

    /**
     * Returns the unique dead-letter record identifier.
     *
     * @return dead-letter record identifier
     */
    public UUID deadLetterId() {
        return deadLetterId;
    }

    /**
     * Returns the conversion job that reached terminal failure.
     *
     * @return conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation that failed.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the processing attempt that reached terminal failure.
     *
     * @return positive processing attempt
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the persisted terminal-failure timestamp.
     *
     * @return terminal-failure timestamp
     */
    public Instant failedAt() {
        return failedAt;
    }

    /**
     * Returns the controlled low-cardinality failure reason.
     *
     * @return registered bounded reason code
     */
    public String reasonCode() {
        return reasonCode;
    }
}
