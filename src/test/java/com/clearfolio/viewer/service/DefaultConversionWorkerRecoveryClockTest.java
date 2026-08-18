package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.artifact.PdfBoxArtifactGenerator;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Verifies that startup recovery uses one caller-supplied clock observation.
 */
class DefaultConversionWorkerRecoveryClockTest {

    @Test
    void staleProcessingRetryUsesTheRecoveryEvaluationTimestamp() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        CapturingStateStore stateStore = new CapturingStateStore(repository);
        ConversionJob staleProcessing = new ConversionJob(
                UUID.randomUUID(),
                "stale.docx",
                "application/octet-stream",
                "hash-recovery-clock",
                10L,
                3
        );
        assertTrue(staleProcessing.markProcessing("worker exited"));
        repository.save(staleProcessing);

        Instant recoveryNow = Instant.now().plus(Duration.ofDays(1));
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                stateStore,
                command -> { },
                new InMemoryArtifactStore(),
                new PdfBoxArtifactGenerator(),
                new ConversionProperties(),
                id -> "/artifacts/" + id + ".pdf"
        );

        int recovered = worker.recoverPendingJobs(recoveryNow, Duration.ofSeconds(60));

        assertEquals(1, recovered);
        assertEquals(recoveryNow, stateStore.retryAt);
    }

    private static final class CapturingStateStore implements ConversionJobStateStore {
        private final ConversionJobStateStore delegate;
        private Instant retryAt;

        private CapturingStateStore(ConversionJobStateStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
            return delegate.claimForProcessing(jobId, now);
        }

        @Override
        public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
            this.retryAt = retryAt;
            delegate.scheduleRetry(jobId, message, retryAt);
        }

        @Override
        public void markSucceeded(UUID jobId, String resourcePath, String message) {
            delegate.markSucceeded(jobId, resourcePath, message);
        }

        @Override
        public void markDeadLettered(UUID jobId, String message) {
            delegate.markDeadLettered(jobId, message);
        }

        @Override
        public boolean retryDeadLettered(UUID jobId, String operatorId) {
            return delegate.retryDeadLettered(jobId, operatorId);
        }
    }
}
