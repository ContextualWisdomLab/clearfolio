package com.clearfolio.viewer.lifecycle;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Provides fixed-memory, process-local serialization for one document lifecycle.
 *
 * <p>Conversion and deletion must not write or remove bytes for the same
 * permanently reserved job identifier at the same time. The registry hashes
 * job identifiers onto a fixed number of lock stripes, so memory use remains
 * bounded even when the service processes many documents. Unrelated jobs may
 * proceed concurrently unless their hashes share a stripe.</p>
 *
 * <p>This registry protects one JVM. Multi-instance deployments must add an
 * equivalent distributed generation fence, object-version precondition, or
 * transactional outbox consumer guarantee at their replaceable storage
 * boundary.</p>
 */
public final class ArtifactLifecycleLockRegistry {

    private static final int DEFAULT_STRIPE_COUNT = 256;
    private static final ArtifactLifecycleLockRegistry SHARED =
            new ArtifactLifecycleLockRegistry(DEFAULT_STRIPE_COUNT);

    private final ReentrantLock[] locks;

    /**
     * Creates a registry with the default fixed number of lock stripes.
     */
    public ArtifactLifecycleLockRegistry() {
        this(DEFAULT_STRIPE_COUNT);
    }

    ArtifactLifecycleLockRegistry(int stripeCount) {
        if (stripeCount <= 0) {
            throw new IllegalArgumentException("stripeCount must be positive");
        }
        this.locks = new ReentrantLock[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    /**
     * Returns the process-wide registry used by conversion and deletion paths.
     *
     * @return shared fixed-memory lifecycle lock registry
     */
    public static ArtifactLifecycleLockRegistry shared() {
        return SHARED;
    }

    /**
     * Executes one action while holding the stripe for a conversion job.
     *
     * <p>The lock is always released, including when the supplied action throws.
     * The action's return value or runtime exception is passed through unchanged.</p>
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param action work that reads or mutates metadata and artifact bytes
     * @param <T> action return type
     * @return value returned by the action
     */
    public <T> T withJobLock(UUID jobId, Supplier<T> action) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Supplier<T> requiredAction = Objects.requireNonNull(action, "action");
        ReentrantLock lock = locks[Math.floorMod(requiredJobId.hashCode(), locks.length)];
        lock.lock();
        try {
            return requiredAction.get();
        } finally {
            lock.unlock();
        }
    }
}
