package com.clearfolio.viewer.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConversionTelemetryEventTest {

    @Test
    void exposesOnlyControlledLowCardinalityAttributes() {
        Instant observedAt = Instant.parse("2026-08-11T11:20:00Z");
        ConversionTelemetryEvent event = new ConversionTelemetryEvent(
                ConversionTelemetryEvent.EventType.RECOVERY,
                ConversionTelemetryEvent.Outcome.RECOVERED,
                observedAt
        );

        assertEquals(ConversionTelemetryEvent.EventType.RECOVERY, event.eventType());
        assertEquals(ConversionTelemetryEvent.Outcome.RECOVERED, event.outcome());
        assertEquals(observedAt, event.observedAt());
        assertEquals(
                Map.of(
                        "clearfolio.conversion.event", "recovery",
                        "clearfolio.conversion.outcome", "recovered"
                ),
                event.attributes()
        );
    }

    @Test
    void attributesAreImmutable() {
        ConversionTelemetryEvent event = new ConversionTelemetryEvent(
                ConversionTelemetryEvent.EventType.EXECUTION,
                ConversionTelemetryEvent.Outcome.SUCCEEDED,
                Instant.parse("2026-08-11T11:21:00Z")
        );

        Map<String, String> attributes = event.attributes();
        assertThrows(
                UnsupportedOperationException.class,
                () -> attributes.put("tenant", "must-never-be-admitted")
        );
    }

    @Test
    void rejectsMissingTelemetryAuthority() {
        Instant observedAt = Instant.parse("2026-08-11T11:22:00Z");

        assertThrows(
                NullPointerException.class,
                () -> new ConversionTelemetryEvent(
                        null,
                        ConversionTelemetryEvent.Outcome.ACCEPTED,
                        observedAt
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new ConversionTelemetryEvent(
                        ConversionTelemetryEvent.EventType.ADMISSION,
                        null,
                        observedAt
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new ConversionTelemetryEvent(
                        ConversionTelemetryEvent.EventType.ADMISSION,
                        ConversionTelemetryEvent.Outcome.ACCEPTED,
                        null
                )
        );
    }

    @Test
    void wireNamesAreStableAndBounded() {
        assertEquals(
                Map.of(
                        ConversionTelemetryEvent.EventType.ADMISSION, "admission",
                        ConversionTelemetryEvent.EventType.EXECUTION, "execution",
                        ConversionTelemetryEvent.EventType.RECOVERY, "recovery",
                        ConversionTelemetryEvent.EventType.CANCELLATION, "cancellation"
                ),
                Map.of(
                        ConversionTelemetryEvent.EventType.ADMISSION,
                        ConversionTelemetryEvent.EventType.ADMISSION.wireName(),
                        ConversionTelemetryEvent.EventType.EXECUTION,
                        ConversionTelemetryEvent.EventType.EXECUTION.wireName(),
                        ConversionTelemetryEvent.EventType.RECOVERY,
                        ConversionTelemetryEvent.EventType.RECOVERY.wireName(),
                        ConversionTelemetryEvent.EventType.CANCELLATION,
                        ConversionTelemetryEvent.EventType.CANCELLATION.wireName()
                )
        );

        assertEquals(
                Map.of(
                        ConversionTelemetryEvent.Outcome.ACCEPTED, "accepted",
                        ConversionTelemetryEvent.Outcome.REPLAYED, "replayed",
                        ConversionTelemetryEvent.Outcome.CAPACITY_REJECTED, "capacity_rejected",
                        ConversionTelemetryEvent.Outcome.SUCCEEDED, "succeeded",
                        ConversionTelemetryEvent.Outcome.RETRYABLE_FAILURE, "retryable_failure",
                        ConversionTelemetryEvent.Outcome.TERMINAL_FAILURE, "terminal_failure",
                        ConversionTelemetryEvent.Outcome.CANCELLED, "cancelled",
                        ConversionTelemetryEvent.Outcome.RECOVERED, "recovered"
                ),
                Map.ofEntries(
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.ACCEPTED,
                                ConversionTelemetryEvent.Outcome.ACCEPTED.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.REPLAYED,
                                ConversionTelemetryEvent.Outcome.REPLAYED.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.CAPACITY_REJECTED,
                                ConversionTelemetryEvent.Outcome.CAPACITY_REJECTED.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.SUCCEEDED,
                                ConversionTelemetryEvent.Outcome.SUCCEEDED.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.RETRYABLE_FAILURE,
                                ConversionTelemetryEvent.Outcome.RETRYABLE_FAILURE.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.TERMINAL_FAILURE,
                                ConversionTelemetryEvent.Outcome.TERMINAL_FAILURE.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.CANCELLED,
                                ConversionTelemetryEvent.Outcome.CANCELLED.wireName()
                        ),
                        Map.entry(
                                ConversionTelemetryEvent.Outcome.RECOVERED,
                                ConversionTelemetryEvent.Outcome.RECOVERED.wireName()
                        )
                )
        );
    }
}
