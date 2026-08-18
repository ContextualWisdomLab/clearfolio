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
 * identifiers onto a fixed number of lock stripes, so memory use remains
 * bounded while unrelated jobs may proceed concurrently unless they share a
 * stripe.</p>
 *
 * <p>This reference registry protects one JVM. Multi-instance deployments must
 * provide an equivalent distributed generation fence, object-version
 * precondition, or transactional outbox consumer guarantee.</p>
 */
public final class ArtifactLifecycleLockRegistry {

    private static final int DEFAULT_STRIPE_COUNT = 256;
    private static final ArtifactLifecycleLockRegistry SHARED =
            new ArtifactLifecycleLockRegistry();

    private final ReentrantLock[] locks;

    private ArtifactLifecycleLockRegistry() {
        this.locks = new ReentrantLock[DEFAULT_STRIPE_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    /**
     * Returns the sole process-wide registry.
     *
     * <p>A single construction path prevents Spring injection and standalone
     * callers from creating disjoint lock domains for the same job identifier.</p>
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
     * @throws NullPointerException when the identifier or action is absent
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
