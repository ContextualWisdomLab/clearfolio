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

import org.junit.jupiter.api.Test;

/**
 * Verifies fixed-memory process-local serialization for artifact lifecycles.
 */
class ArtifactLifecycleLockRegistryTest {

    @Test
    void sameJobCannotEnterTwoArtifactLifecyclesConcurrently() throws Exception {
        ArtifactLifecycleLockRegistry registry = new ArtifactLifecycleLockRegistry(8);
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> registry.withJobLock(jobId, () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            Future<String> second = executor.submit(() -> registry.withJobLock(jobId, () -> {
                secondEntered.countDown();
                return "second";
            }));

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
        ArtifactLifecycleLockRegistry registry = new ArtifactLifecycleLockRegistry(1);
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
    void invalidConstructionAndNullInputsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new ArtifactLifecycleLockRegistry(0));
        ArtifactLifecycleLockRegistry registry = new ArtifactLifecycleLockRegistry(2);

        assertThrows(NullPointerException.class, () -> registry.withJobLock(null, () -> "unused"));
        assertThrows(NullPointerException.class, () -> registry.withJobLock(UUID.randomUUID(), null));
        assertSame(ArtifactLifecycleLockRegistry.shared(), ArtifactLifecycleLockRegistry.shared());
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
