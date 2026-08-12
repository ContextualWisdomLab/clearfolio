package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable durable evidence for one recovered stale conversion attempt.
 *
 * <p>The record binds recovery authority to an exact conversion job generation,
 * attempt, and stale lease. Persisting the recovery and resume timestamps lets a
 * durable execution adapter resume from stored authority instead of reconstructing
 * recovery timing from process-local state.</p>
 */
public final class ConversionRecoveryRecord {

    private final UUID recoveryId;
    private final UUID jobId;
    private final long generation;
    private final int attempt;
    private final UUID staleLeaseId;
    private final Instant recoveredAt;
    private final Instant resumeNotBefore;

    private ConversionRecoveryRecord(
            UUID recoveryId,
            UUID jobId,
            long generation,
            int attempt,
            UUID staleLeaseId,
            Instant recoveredAt,
            Instant resumeNotBefore) {
        this.recoveryId = recoveryId;
        this.jobId = jobId;
        this.generation = generation;
        this.attempt = attempt;
        this.staleLeaseId = staleLeaseId;
        this.recoveredAt = recoveredAt;
        this.resumeNotBefore = resumeNotBefore;
    }

    /**
     * Creates one durable restart-recovery record.
     *
     * @param recoveryId unique identifier for this recovery observation
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation recovered
     * @param attempt non-negative processing attempt observed at recovery
     * @param staleLeaseId worker lease that was declared stale
     * @param recoveredAt persisted instant when recovery was observed
     * @param resumeNotBefore earliest persisted instant when work may resume
     * @return immutable recovery record
     * @throws NullPointerException when a required identifier or timestamp is null
     * @throws IllegalArgumentException when generation, attempt, or timestamp ordering is invalid
     */
    public static ConversionRecoveryRecord recover(
            UUID recoveryId,
            UUID jobId,
            long generation,
            int attempt,
            UUID staleLeaseId,
            Instant recoveredAt,
            Instant resumeNotBefore) {
        UUID requiredRecoveryId = Objects.requireNonNull(recoveryId, "recoveryId");
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        UUID requiredStaleLeaseId = Objects.requireNonNull(staleLeaseId, "staleLeaseId");
        Instant requiredRecoveredAt = Objects.requireNonNull(recoveredAt, "recoveredAt");
        Instant requiredResumeNotBefore = Objects.requireNonNull(resumeNotBefore, "resumeNotBefore");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be non-negative");
        }
        if (requiredResumeNotBefore.isBefore(requiredRecoveredAt)) {
            throw new IllegalArgumentException("resumeNotBefore must not precede recoveredAt");
        }
        return new ConversionRecoveryRecord(
                requiredRecoveryId,
                requiredJobId,
                generation,
                attempt,
                requiredStaleLeaseId,
                requiredRecoveredAt,
                requiredResumeNotBefore
        );
    }

    /**
     * Checks whether candidate stale-work authority exactly matches this recovery.
     *
     * @param candidateJobId candidate conversion-job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @param candidateAttempt candidate processing attempt
     * @param candidateStaleLeaseId candidate stale worker lease identifier
     * @return true only when every authority component exactly matches
     */
    public boolean authorizes(
            UUID candidateJobId,
            long candidateGeneration,
            int candidateAttempt,
            UUID candidateStaleLeaseId) {
        return jobId.equals(candidateJobId)
                && generation == candidateGeneration
                && attempt == candidateAttempt
                && staleLeaseId.equals(candidateStaleLeaseId);
    }

    /**
     * Returns whether the persisted resume boundary has been reached.
     *
     * @param now evaluation timestamp
     * @return true at or after the persisted resume timestamp
     * @throws NullPointerException when {@code now} is null
     */
    public boolean isEligibleAt(Instant now) {
        Instant requiredNow = Objects.requireNonNull(now, "now");
        return !requiredNow.isBefore(resumeNotBefore);
    }

    /**
     * Returns the unique recovery record identifier.
     *
     * @return recovery record identifier
     */
    public UUID recoveryId() {
        return recoveryId;
    }

    /**
     * Returns the recovered conversion-job identifier.
     *
     * @return conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the recovered lifecycle generation.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the processing attempt observed at recovery.
     *
     * @return non-negative processing attempt
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the worker lease that was declared stale.
     *
     * @return stale lease identifier
     */
    public UUID staleLeaseId() {
        return staleLeaseId;
    }

    /**
     * Returns the persisted recovery observation time.
     *
     * @return recovery timestamp
     */
    public Instant recoveredAt() {
        return recoveredAt;
    }

    /**
     * Returns the earliest persisted instant when recovered work may resume.
     *
     * @return resume eligibility timestamp
     */
    public Instant resumeNotBefore() {
        return resumeNotBefore;
    }
}
