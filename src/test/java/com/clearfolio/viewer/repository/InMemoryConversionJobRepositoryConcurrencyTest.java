package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that tenant-scoped deletion, permanent identifier reservation, and
 * the tenant-content secondary index remain consistent under concurrency.
 */
class InMemoryConversionJobRepositoryConcurrencyTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void tenantDeleteKeepsIdentifierReservedAgainstConcurrentReplacement() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
            UUID sharedJobId = UUID.randomUUID();
            CountDownLatch deleteCleanupReached = new CountDownLatch(1);
            CountDownLatch releaseDeleteCleanup = new CountDownLatch(1);
            CountDownLatch replacementTaskStarted = new CountDownLatch(1);
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

                Future<ConversionJob> saveResult = executor.submit(() -> {
                    replacementTaskStarted.countDown();
                    return repository.save(replacement);
                });
                assertTrue(
                        replacementTaskStarted.await(2, TimeUnit.SECONDS),
                        "replacement save task did not start"
                );
                assertFalse(
                        replacementIndexReached.await(100, TimeUnit.MILLISECONDS),
                        "replacement reached index work before delete released the critical section"
                );
                assertFalse(
                        saveResult.isDone(),
                        "replacement save completed before delete cleanup was released"
                );

                releaseDeleteCleanup.countDown();
                assertTrue(deleteResult.get(2, TimeUnit.SECONDS));
                ExecutionException saveFailure = assertThrows(
                        ExecutionException.class,
                        () -> saveResult.get(2, TimeUnit.SECONDS)
                );
                assertInstanceOf(IllegalStateException.class, saveFailure.getCause());
                assertEquals(
                        1L,
                        replacementIndexReached.getCount(),
                        "rejected replacement must not reach secondary-index work"
                );
            } finally {
                releaseDeleteCleanup.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }

            assertTrue(repository.findById(sharedJobId).isEmpty());
            assertTrue(repository.findByTenantAndContentHash(
                    "tenant-north",
                    replacement.getContentHash()
            ).isEmpty());
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
