package com.clearfolio.viewer.observability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Privacy-safe, low-cardinality conversion observation for metrics or traces.
 *
 * <p>This value intentionally carries no tenant, subject, job, file, token,
 * document, exception, or arbitrary reason text. Export adapters can map its
 * controlled attributes to OpenTelemetry or another telemetry backend without
 * widening Clearfolio's confidentiality or metric-cardinality boundary.</p>
 *
 * @param eventType controlled conversion lifecycle surface being observed
 * @param outcome controlled low-cardinality result of the observation
 * @param observedAt instant when the observation was made
 */
public record ConversionTelemetryEvent(
        EventType eventType,
        Outcome outcome,
        Instant observedAt
) {

    /**
     * Creates one bounded conversion telemetry observation.
     *
     * @param eventType controlled conversion lifecycle surface being observed
     * @param outcome controlled low-cardinality result of the observation
     * @param observedAt instant when the observation was made
     * @throws NullPointerException if any required field is absent
     */
    public ConversionTelemetryEvent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(observedAt, "observedAt");
    }

    /**
     * Returns the only backend attributes authorized by this foundation.
     *
     * @return immutable controlled attribute map without customer identifiers
     */
    public Map<String, String> attributes() {
        return Map.of(
                "clearfolio.conversion.event", eventType.wireName(),
                "clearfolio.conversion.outcome", outcome.wireName()
        );
    }

    /**
     * Controlled conversion lifecycle surfaces eligible for telemetry.
     */
    public enum EventType {
        /** Admission and capacity decisions before execution. */
        ADMISSION("admission"),
        /** Conversion execution lifecycle activity. */
        EXECUTION("execution"),
        /** Restart or stale-work recovery activity. */
        RECOVERY("recovery"),
        /** Cancellation lifecycle activity. */
        CANCELLATION("cancellation");

        private final String wireName;

        EventType(String wireName) {
            this.wireName = wireName;
        }

        /**
         * Returns the stable low-cardinality telemetry value.
         *
         * @return backend-safe event type value
         */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Controlled conversion outcomes eligible for telemetry.
     */
    public enum Outcome {
        /** A conversion request was accepted for processing. */
        ACCEPTED("accepted"),
        /** A previously accepted idempotent request was replayed. */
        REPLAYED("replayed"),
        /** Admission was rejected because bounded capacity was unavailable. */
        CAPACITY_REJECTED("capacity_rejected"),
        /** Conversion execution completed successfully. */
        SUCCEEDED("succeeded"),
        /** Execution failed in a way that may be retried. */
        RETRYABLE_FAILURE("retryable_failure"),
        /** Execution failed terminally and must not be retried automatically. */
        TERMINAL_FAILURE("terminal_failure"),
        /** The authorized conversion generation was cancelled. */
        CANCELLED("cancelled"),
        /** Recoverable work was rediscovered after restart or lease recovery. */
        RECOVERED("recovered");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        /**
         * Returns the stable low-cardinality telemetry value.
         *
         * @return backend-safe outcome value
         */
        public String wireName() {
            return wireName;
        }
    }
}
