package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable durable evidence for one generation-fenced conversion attempt.
 *
 * <p>The record binds one attempt to the exact job generation, attempt number,
 * and worker lease that claimed it. A terminal outcome is monotonic: an exact
 * replay is idempotent, while a different outcome or completion timestamp is
 * rejected so stale workers cannot rewrite already-recorded execution evidence.</p>
 */
public final class ConversionAttemptRecord {

    private final UUID attemptId;
    private final UUID jobId;
    private final long generation;
    private final int attempt;
    private final UUID leaseId;
    private final Instant claimedAt;
    private final ConversionAttemptState state;
    private final Instant finishedAt;

    private ConversionAttemptRecord(
            UUID attemptId,
            UUID jobId,
            long generation,
            int attempt,
            UUID leaseId,
            Instant claimedAt,
            ConversionAttemptState state,
            Instant finishedAt) {
        this.attemptId = attemptId;
        this.jobId = jobId;
        this.generation = generation;
        this.attempt = attempt;
        this.leaseId = leaseId;
        this.claimedAt = claimedAt;
        this.state = state;
        this.finishedAt = finishedAt;
    }

    /**
     * Creates the durable identity for one newly claimed conversion attempt.
     *
     * @param attemptId unique identifier for this exact execution attempt
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation owned by the attempt
     * @param attempt positive attempt sequence number for the job generation
     * @param leaseId exact worker-lease identifier authorizing publication
     * @param claimedAt persisted instant when the worker claim became authoritative
     * @return immutable claimed attempt record
     * @throws NullPointerException when an identifier or timestamp is null
     * @throws IllegalArgumentException when generation or attempt is not positive
     */
    public static ConversionAttemptRecord claim(
            UUID attemptId,
            UUID jobId,
            long generation,
            int attempt,
            UUID leaseId,
            Instant claimedAt) {
        UUID requiredAttemptId = Objects.requireNonNull(attemptId, "attemptId");
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        UUID requiredLeaseId = Objects.requireNonNull(leaseId, "leaseId");
        Instant requiredClaimedAt = Objects.requireNonNull(claimedAt, "claimedAt");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        return new ConversionAttemptRecord(
                requiredAttemptId,
                requiredJobId,
                generation,
                attempt,
                requiredLeaseId,
                requiredClaimedAt,
                ConversionAttemptState.CLAIMED,
                null
        );
    }

    /**
     * Records the terminal outcome for this exact attempt.
     *
     * <p>Repeating the identical terminal transition is idempotent. Any later
     * attempt to change the outcome or completion instant is rejected.</p>
     *
     * @param terminalState terminal state to record
     * @param completionTime persisted completion instant, not before the claim
     * @return a terminal immutable record, or this instance for an exact replay
     * @throws NullPointerException when a state or completion timestamp is null
     * @throws IllegalArgumentException when the state is not terminal or completion precedes claim
     * @throws IllegalStateException when terminal evidence would be rewritten
     */
    public ConversionAttemptRecord finish(
            ConversionAttemptState terminalState,
            Instant completionTime) {
        ConversionAttemptState requiredState = Objects.requireNonNull(terminalState, "terminalState");
        Instant requiredFinishedAt = Objects.requireNonNull(completionTime, "completionTime");
        if (requiredState == ConversionAttemptState.CLAIMED) {
            throw new IllegalArgumentException("terminalState must be terminal");
        }
        if (requiredFinishedAt.isBefore(claimedAt)) {
            throw new IllegalArgumentException("completionTime must not precede claimedAt");
        }
        if (state != ConversionAttemptState.CLAIMED) {
            if (state == requiredState && finishedAt.equals(requiredFinishedAt)) {
                return this;
            }
            throw new IllegalStateException("attempt already has terminal evidence");
        }
        return new ConversionAttemptRecord(
                attemptId,
                jobId,
                generation,
                attempt,
                leaseId,
                claimedAt,
                requiredState,
                requiredFinishedAt
        );
    }

    /**
     * Checks whether candidate publication authority matches this exact worker claim.
     *
     * <p>Failed terminal attempts no longer authorize artifact publication. A
     * successful terminal attempt remains authorized so persistence of the
     * success outcome may precede final artifact publication. The attempt number
     * is an explicit fence: another attempt under the same job, generation, and
     * lease cannot reuse this record's publication authority.</p>
     *
     * @param candidateJobId candidate permanently reserved job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @param candidateAttempt candidate positive attempt sequence number
     * @param candidateLeaseId candidate worker-lease identifier
     * @return true only when the attempt may publish and all immutable execution
     *         coordinates match; false when a candidate identifier is null or
     *         the attempt failed terminally
     */
    public boolean authorizes(
            UUID candidateJobId,
            long candidateGeneration,
            int candidateAttempt,
            UUID candidateLeaseId) {
        boolean publicationState = state == ConversionAttemptState.CLAIMED
                || state == ConversionAttemptState.SUCCEEDED;
        return publicationState
                && jobId.equals(candidateJobId)
                && generation == candidateGeneration
                && attempt == candidateAttempt
                && leaseId.equals(candidateLeaseId);
    }

    /**
     * Returns the unique identifier for this exact execution attempt.
     *
     * @return attempt identifier
     */
    public UUID attemptId() {
        return attemptId;
    }

    /**
     * Returns the permanently reserved conversion-job identifier.
     *
     * @return job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation fenced by this attempt.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns this attempt's positive sequence number within the job generation.
     *
     * @return positive attempt number
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the worker-lease identifier that owns publication authority.
     *
     * @return worker-lease identifier
     */
    public UUID leaseId() {
        return leaseId;
    }

    /**
     * Returns the persisted instant when this attempt was claimed.
     *
     * @return claim timestamp
     */
    public Instant claimedAt() {
        return claimedAt;
    }

    /**
     * Returns the current durable attempt state.
     *
     * @return claimed or terminal state
     */
    public ConversionAttemptState state() {
        return state;
    }

    /**
     * Returns the persisted terminal timestamp when the attempt has finished.
     *
     * @return terminal timestamp, or null while the attempt remains claimed
     */
    public Instant finishedAt() {
        return finishedAt;
    }
}
