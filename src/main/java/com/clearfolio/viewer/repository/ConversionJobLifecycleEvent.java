package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * Append-only lifecycle event for a conversion job transition.
 *
 * @param eventId event identifier
 * @param jobId conversion job identifier
 * @param tenantId tenant isolation boundary
 * @param eventType controlled lifecycle event type
 * @param eventVersion schema version
 * @param occurredAt event timestamp
 * @param statusBefore lifecycle status before the transition
 * @param statusAfter lifecycle status after the transition
 * @param attemptCount attempt count after the transition
 * @param retryAt next retry instant after the transition
 */
public record ConversionJobLifecycleEvent(
        UUID eventId,
        UUID jobId,
        String tenantId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        ConversionJobStatus statusBefore,
        ConversionJobStatus statusAfter,
        int attemptCount,
        Instant retryAt
) {

    /**
     * Current event schema version.
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Creates a conversion job lifecycle event.
     */
    public ConversionJobLifecycleEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(statusAfter, "statusAfter");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
