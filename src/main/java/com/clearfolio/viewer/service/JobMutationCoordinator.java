package com.clearfolio.viewer.service;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Coordinates mutually exclusive mutations for the same conversion job inside one JVM.
 *
 * <p>The current repository is process-local, so a fixed striped lock set is sufficient
 * to keep worker publication and administrative deletion on one ownership boundary
 * without retaining one lock object per historical job identifier.
 */
final class JobMutationCoordinator {

    private static final int LOCK_STRIPE_COUNT = 256;
    private static final Object[] LOCKS = IntStream.range(0, LOCK_STRIPE_COUNT)
            .mapToObj(ignored -> new Object())
            .toArray(Object[]::new);

    private JobMutationCoordinator() {
    }

    static <T> T withJobLock(UUID jobId, Supplier<T> action) {
        Object lock = LOCKS[(jobId.hashCode() & Integer.MAX_VALUE) % LOCKS.length];
        synchronized (lock) {
            return action.get();
        }
    }
}
