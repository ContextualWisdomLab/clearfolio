package com.clearfolio.viewer.lifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dependency-free low-cardinality evidence for durable artifact deletion.
 *
 * <p>The component retains aggregate outcomes only. Tenant identifiers, job
 * identifiers, checksums, exception messages, filenames, tokens, and storage
 * paths are never accepted as dimensions. A host may bridge these values into
 * its existing observability plane without coupling this module to a metrics
 * framework or management HTTP endpoint.</p>
 */
@Component
public final class ArtifactDeletionMetrics {

    private final ArtifactDeletionReceiptStore receiptStore;
    private final LongAdder completedAttempts = new LongAdder();
    private final LongAdder failedAttempts = new LongAdder();

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
}
