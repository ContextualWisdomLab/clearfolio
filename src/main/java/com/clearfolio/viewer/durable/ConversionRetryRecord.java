package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable durable schedule record for one conversion retry attempt.
 *
 * <p>The record binds retry authority to an exact permanently identified job
 * generation and attempt number. Persisting this state allows an execution
 * adapter to recover retry timing after process restart instead of treating an
 * in-memory delayed task as the source of truth.</p>
 */
public final class ConversionRetryRecord {

    private final UUID retryId;
    private final UUID jobId;
    private final long generation;
    private final int attempt;
    private final Instant dueAt;

    private ConversionRetryRecord(
            UUID retryId,
            UUID jobId,
            long generation,
            int attempt,
            Instant dueAt) {
        this.retryId = retryId;
        this.jobId = jobId;
        this.generation = generation;
        this.attempt = attempt;
        this.dueAt = dueAt;
    }

    /**
     * Creates one durable retry schedule entry.
     *
     * @param retryId unique identifier for this scheduled retry record
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation authorized for the retry
     * @param attempt positive attempt number represented by this schedule entry
     * @param dueAt persisted instant when the retry becomes eligible
     * @return immutable retry schedule record
     * @throws NullPointerException when an identifier or due timestamp is null
     * @throws IllegalArgumentException when generation or attempt is not positive
     */
    public static ConversionRetryRecord schedule(
            UUID retryId,
            UUID jobId,
            long generation,
            int attempt,
            Instant dueAt) {
        UUID requiredRetryId = Objects.requireNonNull(retryId, "retryId");
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Instant requiredDueAt = Objects.requireNonNull(dueAt, "dueAt");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        return new ConversionRetryRecord(
                requiredRetryId,
                requiredJobId,
                generation,
                attempt,
                requiredDueAt
        );
    }

    /**
     * Returns whether this retry is eligible at the supplied instant.
     *
     * @param now evaluation timestamp
     * @return true at or after the persisted due timestamp
     * @throws NullPointerException when {@code now} is null
     */
    public boolean isDue(Instant now) {
        Instant requiredNow = Objects.requireNonNull(now, "now");
        return !requiredNow.isBefore(dueAt);
    }

    /**
     * Checks whether candidate job authority matches this scheduled generation.
     *
     * @param candidateJobId candidate conversion-job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @return true only when both authority components exactly match
     */
    public boolean authorizes(UUID candidateJobId, long candidateGeneration) {
        return jobId.equals(candidateJobId) && generation == candidateGeneration;
    }

    /**
     * Returns the unique retry schedule identifier.
     *
     * @return retry record identifier
     */
    public UUID retryId() {
        return retryId;
    }

    /**
     * Returns the conversion job whose retry is scheduled.
     *
     * @return conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation fenced by this retry record.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the positive attempt number represented by this record.
     *
     * @return retry attempt number
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the persisted instant when the retry becomes eligible.
     *
     * @return due timestamp
     */
    public Instant dueAt() {
        return dueAt;
    }
}
