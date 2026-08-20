package com.clearfolio.viewer.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable authority result for one conversion-admission attempt.
 *
 * <p>The factories make contradictory states unrepresentable: accepted and
 * idempotent outcomes carry a canonical job identifier but no retry hint,
 * while a capacity rejection carries only positive retry guidance and no job
 * identifier. This distinction is a prerequisite for issue #312's rule that a
 * request which was never durably accepted must not be reported as an accepted
 * conversion job.</p>
 */
public final class ConversionAdmissionResult {

    private final ConversionAdmissionDecision decision;
    private final UUID jobId;
    private final Duration retryAfter;

    private ConversionAdmissionResult(
            ConversionAdmissionDecision decision,
            UUID jobId,
            Duration retryAfter) {
        this.decision = decision;
        this.jobId = jobId;
        this.retryAfter = retryAfter;
    }

    /**
     * Creates an outcome for a newly accepted canonical job.
     *
     * @param jobId canonical job identifier whose acceptance authority was established
     * @return accepted admission result
     * @throws NullPointerException when {@code jobId} is null
     */
    public static ConversionAdmissionResult accepted(UUID jobId) {
        return new ConversionAdmissionResult(
                ConversionAdmissionDecision.ACCEPTED,
                Objects.requireNonNull(jobId, "jobId"),
                null
        );
    }

    /**
     * Creates an outcome for an idempotent replay of an already accepted job.
     *
     * @param jobId canonical previously accepted job identifier
     * @return idempotent replay admission result
     * @throws NullPointerException when {@code jobId} is null
     */
    public static ConversionAdmissionResult idempotentReplay(UUID jobId) {
        return new ConversionAdmissionResult(
                ConversionAdmissionDecision.IDEMPOTENT_REPLAY,
                Objects.requireNonNull(jobId, "jobId"),
                null
        );
    }

    /**
     * Creates a capacity rejection for a request that was not accepted.
     *
     * @param retryAfter positive bounded guidance before a later admission attempt
     * @return capacity-rejected admission result
     * @throws NullPointerException when {@code retryAfter} is null
     * @throws IllegalArgumentException when {@code retryAfter} is zero or negative
     */
    public static ConversionAdmissionResult capacityRejected(Duration retryAfter) {
        Duration requiredRetryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
        if (requiredRetryAfter.isZero() || requiredRetryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        return new ConversionAdmissionResult(
                ConversionAdmissionDecision.CAPACITY_REJECTED,
                null,
                requiredRetryAfter
        );
    }

    /**
     * Returns the mutually exclusive admission decision.
     *
     * @return admission decision
     */
    public ConversionAdmissionDecision decision() {
        return decision;
    }

    /**
     * Returns the canonical job identifier when this request is accepted or replayed.
     *
     * @return canonical job identifier, or empty for a capacity rejection
     */
    public Optional<UUID> jobId() {
        return Optional.ofNullable(jobId);
    }

    /**
     * Returns retry guidance only when the request was rejected for capacity.
     *
     * @return positive retry guidance, or empty for accepted/replayed outcomes
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
