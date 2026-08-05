package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
            CountDownLatch replacementIndexReached = new CountDownLatch(1);
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
                Future<Boolean> deleteResult = executor.submit(
                        () -> repository.deleteByTenantAndId("tenant-north", sharedJobId)
                );
                assertTrue(
                        deleteCleanupReached.await(2, TimeUnit.SECONDS),
                        "tenant delete did not reach secondary-index cleanup"
                );

                Future<ConversionJob> saveResult = executor.submit(() -> repository.save(replacement));
                boolean replacementEnteredBeforeDeleteReleased = replacementIndexReached.await(
                        1,
                        TimeUnit.SECONDS
                );
                if (replacementEnteredBeforeDeleteReleased) {
                    assertSame(replacement, saveResult.get(2, TimeUnit.SECONDS));
                }

                releaseDeleteCleanup.countDown();
                assertTrue(deleteResult.get(2, TimeUnit.SECONDS));
                assertSame(replacement, saveResult.get(2, TimeUnit.SECONDS));
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
