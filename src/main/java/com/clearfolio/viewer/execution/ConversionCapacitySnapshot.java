package com.clearfolio.viewer.execution;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable observation of conversion worker and queue capacity.
 *
 * <p>The snapshot provides one internally consistent input for admission and
 * backpressure policy. Aggregate arithmetic is deliberately widened to
 * {@code long} so large, otherwise valid integer configuration values cannot
 * overflow while capacity is evaluated.</p>
 *
 * @param workerCapacity positive configured number of concurrent worker slots
 * @param activeWorkers observed number of active workers from zero through worker capacity
 * @param queueCapacity nonnegative configured number of queued-job slots
 * @param queuedJobs observed queue depth from zero through queue capacity
 * @param observedAt instant at which all capacity fields were observed together
 */
public record ConversionCapacitySnapshot(
        int workerCapacity,
        int activeWorkers,
        int queueCapacity,
        int queuedJobs,
        Instant observedAt) {

    /**
     * Validates and creates a conversion-capacity snapshot.
     *
     * @throws IllegalArgumentException when worker capacity is not positive, a count is negative,
     *         or an observed count exceeds its configured capacity
     * @throws NullPointerException when {@code observedAt} is null
     */
    public ConversionCapacitySnapshot {
        if (workerCapacity <= 0) {
            throw new IllegalArgumentException("workerCapacity must be positive");
        }
        if (activeWorkers < 0 || activeWorkers > workerCapacity) {
            throw new IllegalArgumentException("activeWorkers must be within worker capacity");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must be nonnegative");
        }
        if (queuedJobs < 0 || queuedJobs > queueCapacity) {
            throw new IllegalArgumentException("queuedJobs must be within queue capacity");
        }
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    /**
     * Returns the combined worker-and-queue capacity without integer overflow.
     *
     * @return total configured worker and queue slots
     */
    public long totalCapacity() {
        return (long) workerCapacity + queueCapacity;
    }

    /**
     * Returns the currently unoccupied worker and queue slots.
     *
     * @return number of conversion requests that can be accepted before saturation
     */
    public long remainingCapacity() {
        return (long) workerCapacity - activeWorkers
                + (long) queueCapacity - queuedJobs;
    }

    /**
     * Reports whether every worker and queue slot is occupied.
     *
     * @return {@code true} when no worker or queue capacity remains
     */
    public boolean saturated() {
        return remainingCapacity() == 0L;
    }
}
