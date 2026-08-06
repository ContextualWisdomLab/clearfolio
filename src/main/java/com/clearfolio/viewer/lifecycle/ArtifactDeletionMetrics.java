package com.clearfolio.viewer.lifecycle;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Low-cardinality operational metrics for durable artifact deletion.
 *
 * <p>The metric labels are fixed outcome values. Tenant identifiers, job
 * identifiers, checksums, exception messages, filenames, tokens, and storage
 * paths are never used as meter tags.</p>
 */
@Component
public final class ArtifactDeletionMetrics {

    private static final String ATTEMPT_METER_NAME = "clearfolio.artifact.deletion.attempts";
    private static final String PENDING_METER_NAME = "clearfolio.artifact.deletion.pending";

    private final Counter completedAttempts;
    private final Counter failedAttempts;

    /**
     * Registers cleanup counters and the pending-receipt gauge.
     *
     * @param meterRegistry application meter registry
     * @param receiptStore durable receipt store that supplies the pending count
     */
    @Autowired
    public ArtifactDeletionMetrics(
            MeterRegistry meterRegistry,
            ArtifactDeletionReceiptStore receiptStore
    ) {
        MeterRegistry requiredRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        ArtifactDeletionReceiptStore requiredStore = Objects.requireNonNull(receiptStore, "receiptStore");
        this.completedAttempts = Counter.builder(ATTEMPT_METER_NAME)
                .description("Artifact deletion attempts that reached durable completion")
                .tag("outcome", "completed")
                .register(requiredRegistry);
        this.failedAttempts = Counter.builder(ATTEMPT_METER_NAME)
                .description("Artifact deletion attempts retained for retry")
                .tag("outcome", "failed")
                .register(requiredRegistry);
        Gauge.builder(PENDING_METER_NAME, requiredStore, ArtifactDeletionReceiptStore::pendingCount)
                .description("Incomplete durable artifact deletion receipts")
                .register(requiredRegistry);
    }

    /**
     * Records one cleanup attempt that reached durable completion.
     */
    public void recordCompleted() {
        completedAttempts.increment();
    }

    /**
     * Records one cleanup attempt retained for controlled retry.
     */
    public void recordFailed() {
        failedAttempts.increment();
    }
}
