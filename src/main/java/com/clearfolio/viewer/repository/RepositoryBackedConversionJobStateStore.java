package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * State-store adapter for repository implementations that have not yet
 * implemented transition methods directly.
 */
public final class RepositoryBackedConversionJobStateStore implements ConversionJobStateStore {

    private final ConversionJobRepository repository;

    /**
     * Creates an adapter around an existing repository.
     *
     * @param repository conversion job repository
     */
    public RepositoryBackedConversionJobStateStore(ConversionJobRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
        Optional<ConversionJob> job = repository.findById(jobId);
        if (job.isEmpty() || !job.get().isReadyForProcessing(now)) {
            return Optional.empty();
        }

        if (!job.get().markProcessing("conversion started")) {
            return Optional.empty();
        }

        repository.save(job.get());
        return job;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
        repository.findById(jobId).ifPresent(job -> {
            job.markRetryScheduled(message, retryAt);
            repository.save(job);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markSucceeded(UUID jobId, String resourcePath, String message) {
        repository.findById(jobId).ifPresent(job -> {
            job.markSucceeded(resourcePath, message);
            repository.save(job);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markDeadLettered(UUID jobId, String message) {
        repository.findById(jobId).ifPresent(job -> {
            ConversionJobStatus status = job.getStatus();
            if (status == ConversionJobStatus.SUBMITTED || status == ConversionJobStatus.PROCESSING) {
                job.markDeadLettered(message);
                repository.save(job);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryDeadLettered(UUID jobId, String operatorId) {
        Optional<ConversionJob> job = repository.findById(jobId);
        if (job.isEmpty() || !job.get().retryDeadLetteredToSubmitted(operatorId)) {
            return false;
        }

        repository.save(job.get());
        return true;
    }
}
