package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Explicit lifecycle transition boundary for conversion jobs.
 */
public interface ConversionJobStateStore {

    /**
     * Claims a ready job for processing.
     *
     * @param jobId conversion job identifier
     * @param now evaluation timestamp
     * @return claimed conversion job when the transition succeeds
     */
    Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now);

    /**
     * Schedules a retry for a conversion job.
     *
     * @param jobId conversion job identifier
     * @param message retry status message
     * @param retryAt next retry instant
     */
    void scheduleRetry(UUID jobId, String message, Instant retryAt);

    /**
     * Marks a conversion job as successfully completed.
     *
     * @param jobId conversion job identifier
     * @param resourcePath converted artifact path
     * @param message completion status message
     */
    void markSucceeded(UUID jobId, String resourcePath, String message);

    /**
     * Marks an active conversion job as dead-lettered.
     *
     * @param jobId conversion job identifier
     * @param message dead-letter status message
     */
    void markDeadLettered(UUID jobId, String message);

    /**
     * Resets a dead-lettered conversion job to submitted state.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier that accepted retry
     * @return true when the retry transition succeeds
     */
    boolean retryDeadLettered(UUID jobId, String operatorId);
}
