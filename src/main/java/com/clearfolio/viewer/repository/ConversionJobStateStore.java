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
     * Outcomes from one atomic tenant-scoped dead-letter retry attempt.
     */
    enum TenantRetryOutcome {
        /** An owned dead-lettered job moved back to submitted state. */
        ACCEPTED,
        /** The job was absent or was owned by a different tenant. */
        NOT_FOUND,
        /** The owned job existed but was not eligible for retry. */
        NOT_ELIGIBLE
    }

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

    /**
     * Retries one dead-lettered job only when it belongs to the supplied tenant.
     *
     * <p>The default fails closed without invoking the legacy unscoped retry.
     * Durable state-store adapters must override this method with one atomic
     * tenant selection and state transition.</p>
     *
     * @param tenantId authenticated tenant identifier
     * @param jobId conversion job identifier
     * @param operatorId privacy-safe operator fingerprint
     * @return accepted, concealed not-found, or not-eligible outcome
     */
    default TenantRetryOutcome retryDeadLetteredForTenant(
            String tenantId,
            UUID jobId,
            String operatorId
    ) {
        return TenantRetryOutcome.NOT_FOUND;
    }
}
