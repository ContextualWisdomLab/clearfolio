package com.clearfolio.viewer.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dependency-free low-cardinality evidence for durable artifact deletion.
 *
 * <p>The component retains aggregate outcomes and recovery-batch durations
 * only. Tenant identifiers, job identifiers, checksums, exception messages,
 * filenames, tokens, and storage paths are never accepted as dimensions. A
 * host may bridge these values into its existing observability plane without
 * coupling this module to a metrics framework or management HTTP endpoint.</p>
 */
@Component
public final class ArtifactDeletionMetrics {

    private final ArtifactDeletionReceiptStore receiptStore;
    private final LongAdder completedAttempts = new LongAdder();
    private final LongAdder failedAttempts = new LongAdder();
    private final LongAdder recoveryBatchRuns = new LongAdder();
    private final LongAdder recoveryBatchTotalNanos = new LongAdder();
    private final AtomicLong recoveryBatchMaximumNanos = new AtomicLong();

    /**
     * Creates aggregate cleanup evidence backed by the durable receipt store.
     *
     * @param receiptStore durable receipt store that supplies the pending count
     * @throws NullPointerException when the receipt store is absent
     */
    @Autowired
    public ArtifactDeletionMetrics(ArtifactDeletionReceiptStore receiptStore) {
        this.receiptStore = Objects.requireNonNull(receiptStore, "receiptStore");
    }

    /** Records one cleanup attempt that reached durable completion. */
    public void recordCompleted() {
        completedAttempts.increment();
    }

    /** Records one cleanup attempt retained for controlled retry. */
    public void recordFailed() {
        failedAttempts.increment();
    }

    /**
     * Records the elapsed time of one bounded recovery batch.
     *
     * <p>The aggregate has no tenant, job, exception, path, or content
     * dimension. Zero-duration measurements are valid because a monotonic clock
     * may return the same tick for an empty batch.</p>
     *
     * @param duration non-negative elapsed recovery duration
     * @throws NullPointerException when the duration is absent
     * @throws IllegalArgumentException when the duration is negative
     * @throws ArithmeticException when the duration cannot be represented in nanoseconds
     */
    public void recordRecoveryBatchDuration(Duration duration) {
        Duration requiredDuration = Objects.requireNonNull(duration, "duration");
        if (requiredDuration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        long nanos = requiredDuration.toNanos();
        recoveryBatchRuns.increment();
        recoveryBatchTotalNanos.add(nanos);
        recoveryBatchMaximumNanos.accumulateAndGet(nanos, Math::max);
    }

    /**
     * Returns the cumulative completed-attempt count.
     *
     * @return completed cleanup attempt count
     */
    public long completedAttempts() {
        return completedAttempts.sum();
    }

    /**
     * Returns the cumulative failed-attempt count.
     *
     * @return failed cleanup attempt count
     */
    public long failedAttempts() {
        return failedAttempts.sum();
    }

    /**
     * Returns the current number of incomplete durable receipts.
     *
     * @return pending receipt count
     */
    public int pendingReceipts() {
        return receiptStore.pendingCount();
    }

    /**
     * Returns the number of measured bounded recovery batches.
     *
     * @return recovery batch measurement count
     */
    public long recoveryBatchRuns() {
        return recoveryBatchRuns.sum();
    }

    /**
     * Returns the total elapsed time across measured recovery batches.
     *
     * @return cumulative recovery duration
     */
    public Duration recoveryBatchTotalDuration() {
        return Duration.ofNanos(recoveryBatchTotalNanos.sum());
    }

    /**
     * Returns the longest measured recovery batch duration.
     *
     * @return maximum recovery duration, or zero before the first measurement
     */
    public Duration recoveryBatchMaximumDuration() {
        return Duration.ofNanos(recoveryBatchMaximumNanos.get());
    }
}
