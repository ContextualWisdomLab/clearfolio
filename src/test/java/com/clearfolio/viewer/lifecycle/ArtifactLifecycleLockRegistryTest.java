package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Verifies fixed-memory process-local serialization for artifact lifecycles.
 */
class ArtifactLifecycleLockRegistryTest {

    @Test
    void sameJobCannotEnterTwoArtifactLifecyclesConcurrently() throws Exception {
        ArtifactLifecycleLockRegistry registry = ArtifactLifecycleLockRegistry.shared();
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> registry.withJobLock(jobId, () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            Future<String> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return registry.withJobLock(jobId, () -> {
                    secondEntered.countDown();
                    return "second";
                });
            });

            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertTrue(awaitBlockedOnLock(secondThread.get()));
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();

            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void actionFailureReleasesStripeForFollowingWork() {
        ArtifactLifecycleLockRegistry registry = ArtifactLifecycleLockRegistry.shared();
        UUID jobId = UUID.randomUUID();
        IllegalStateException expected = new IllegalStateException("controlled failure");

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> registry.withJobLock(jobId, () -> {
                    throw expected;
                })
        );

        assertSame(expected, actual);
        assertEquals("recovered", registry.withJobLock(jobId, () -> "recovered"));
    }

    @Test
    void constructionAndNullInputsFailClosed() {
        ArtifactLifecycleLockRegistry registry = ArtifactLifecycleLockRegistry.shared();

        assertEquals(0, ArtifactLifecycleLockRegistry.class.getConstructors().length);
        assertSame(registry, ArtifactLifecycleLockRegistry.shared());
        assertThrows(NullPointerException.class, () -> registry.withJobLock(null, () -> "unused"));
        assertThrows(NullPointerException.class, () -> registry.withJobLock(UUID.randomUUID(), null));
    }

    private static boolean awaitBlockedOnLock(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        }
    }
}
