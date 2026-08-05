package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that the primary job map and tenant-content secondary index remain
 * consistent when tenant-scoped deletion races with replacement of the same
 * job identifier.
 */
class InMemoryConversionJobRepositoryConcurrencyTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void tenantDeleteCannotRemoveTheIndexOfAConcurrentReplacement() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
            UUID sharedJobId = UUID.randomUUID();
            CountDownLatch deleteCleanupReached = new CountDownLatch(1);
            CountDownLatch releaseDeleteCleanup = new CountDownLatch(1);
            CountDownLatch replacementTaskStarted = new CountDownLatch(1);
            CountDownLatch replacementIndexReached = new CountDownLatch(1);
            AtomicReference<Thread> deleteThread = new AtomicReference<>();
            AtomicReference<Thread> replacementThread = new AtomicReference<>();
            BlockingContentHashJob original = new BlockingContentHashJob(
                    sharedJobId,
                    "tenant-north",
                    "north-original.pdf",
                    "shared-content-hash",
                    deleteCleanupReached,
                    releaseDeleteCleanup
            );
            SignallingContentHashJob replacement = new SignallingContentHashJob(
                    sharedJobId,
                    "tenant-north",
                    "north-replacement.pdf",
                    "shared-content-hash",
                    replacementIndexReached
            );
            repository.save(original);
            original.arm();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> deleteResult = executor.submit(() -> {
                    deleteThread.set(Thread.currentThread());
                    return repository.deleteByTenantAndId("tenant-north", sharedJobId);
                });
                assertTrue(
                        deleteCleanupReached.await(2, TimeUnit.SECONDS),
                        "tenant delete did not reach secondary-index cleanup"
                );

                Future<ConversionJob> saveResult = executor.submit(() -> {
                    replacementThread.set(Thread.currentThread());
                    replacementTaskStarted.countDown();
                    return repository.save(replacement);
                });
                assertTrue(
                        replacementTaskStarted.await(2, TimeUnit.SECONDS),
                        "replacement save task did not start"
                );
                assertBlockedByDeleteCriticalSection(
                        replacementThread.get(),
                        deleteThread.get(),
                        saveResult
                );
                assertTrue(
                        replacementIndexReached.getCount() == 1L,
                        "replacement reached index work before delete released the critical section"
                );

                releaseDeleteCleanup.countDown();
                assertTrue(deleteResult.get(2, TimeUnit.SECONDS));
                assertSame(replacement, saveResult.get(2, TimeUnit.SECONDS));
                assertTrue(
                        replacementIndexReached.await(2, TimeUnit.SECONDS),
                        "replacement did not reach index work after delete released the critical section"
                );
            } finally {
                releaseDeleteCleanup.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }

            assertSame(
                    replacement,
                    repository.findByTenantAndContentHash(
                            "tenant-north",
                            replacement.getContentHash()
                    ).orElseThrow()
            );
        });
    }

    /**
     * Requires the replacement save to be blocked on the repository monitor
     * still owned by the paused delete operation. A completed save is a failure,
     * because it means the test did not establish the intended interleaving.
     *
     * @param replacementThread thread executing the replacement save
     * @param deleteThread thread holding the repository critical section
     * @param saveResult replacement-save future used to reject early completion
     */
    private static void assertBlockedByDeleteCriticalSection(
            Thread replacementThread,
            Thread deleteThread,
            Future<?> saveResult
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            assertTrue(
                    !saveResult.isDone(),
                    "replacement save completed before delete cleanup was released"
            );
            if (replacementThread.getState() == Thread.State.BLOCKED) {
                ThreadInfo threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(
                        replacementThread.threadId()
                );
                assertTrue(threadInfo != null, "replacement thread metadata was unavailable");
                assertTrue(
                        threadInfo.getLockOwnerId() == deleteThread.threadId(),
                        "replacement save blocked on a monitor not owned by the delete operation"
                );
                return;
            }
            Thread.onSpinWait();
        }
        assertTrue(
                false,
                "replacement save did not block on the delete critical section; observed state="
                        + replacementThread.getState()
        );
    }

    /**
     * Conversion job that pauses the first armed content-hash read so a test can
     * deterministically position another operation between primary-map removal
     * and secondary-index cleanup.
     */
    private static final class BlockingContentHashJob extends ConversionJob {

        private final CountDownLatch cleanupReached;
        private final CountDownLatch cleanupRelease;
        private final AtomicBoolean blocked = new AtomicBoolean();
        private volatile boolean armed;

        private BlockingContentHashJob(
                UUID jobId,
                String tenantId,
                String fileName,
                String contentHash,
                CountDownLatch cleanupReached,
                CountDownLatch cleanupRelease
        ) {
            super(
                    jobId,
                    tenantId,
                    "owner",
                    fileName,
                    "application/pdf",
                    contentHash,
                    100L,
                    3
            );
            this.cleanupReached = cleanupReached;
            this.cleanupRelease = cleanupRelease;
        }

        private void arm() {
            armed = true;
        }

        @Override
        public String getContentHash() {
            if (armed && blocked.compareAndSet(false, true)) {
                cleanupReached.countDown();
                await(cleanupRelease);
            }
            return super.getContentHash();
        }
    }

    /**
     * Conversion job that signals when a concurrent save reaches index work.
     */
    private static final class SignallingContentHashJob extends ConversionJob {

        private final CountDownLatch indexReached;

        private SignallingContentHashJob(
                UUID jobId,
                String tenantId,
                String fileName,
                String contentHash,
                CountDownLatch indexReached
        ) {
            super(
                    jobId,
                    tenantId,
                    "owner",
                    fileName,
                    "application/pdf",
                    contentHash,
                    100L,
                    3
            );
            this.indexReached = indexReached;
        }

        @Override
        public String getContentHash() {
            indexReached.countDown();
            return super.getContentHash();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test was interrupted", exception);
        }
    }
}
