package com.clearfolio.viewer.api;

import java.time.Instant;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * API payload describing the current state and retry history of one conversion
 * job.
 *
 * @param jobId stable conversion-job identifier
 * @param tenantId tenant that owns the job and every derived artifact
 * @param fileName original client-visible source filename
 * @param status current conversion lifecycle state
 * @param message operator-safe status or failure explanation
 * @param convertedResourcePath internal converted-resource reference when one is
 *        available
 * @param createdAt time at which the job was accepted
 * @param startedAt time at which processing most recently began, or
 *        {@code null} before processing
 * @param completedAt time at which the job reached a terminal state, or
 *        {@code null} while incomplete
 * @param attemptCount number of processing attempts already started
 * @param maxAttempts maximum number of attempts allowed before dead lettering
 * @param retryAt earliest time at which another attempt may begin, or
 *        {@code null} when no retry is scheduled
 * @param deadLettered whether automatic processing has exhausted its retry
 *        allowance
 */
public record ConversionJobStatusResponse(
        UUID jobId,
        String tenantId,
        String fileName,
        String status,
        String message,
        String convertedResourcePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        int attemptCount,
        int maxAttempts,
        Instant retryAt,
        boolean deadLettered
) {

    /**
     * Creates a response payload from the domain conversion job model.
     *
     * @param job conversion job model
     * @return mapped API response
     */
    public static ConversionJobStatusResponse from(ConversionJob job) {
        return new ConversionJobStatusResponse(
                job.getJobId(),
                job.getTenantId(),
                job.getOriginalFileName(),
                job.getStatus().name(),
                job.getStatusMessage(),
                job.getConvertedResourcePath(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getRetryAt(),
                job.isDeadLettered()
        );
    }
}
