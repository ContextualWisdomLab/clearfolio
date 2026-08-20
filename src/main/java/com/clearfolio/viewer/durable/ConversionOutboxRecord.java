package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable durable-dispatch identity for one accepted conversion generation.
 *
 * <p>A pending record exists only after acceptance authority has been
 * established for the bound job generation. Dispatch acknowledgement creates a
 * new terminal snapshot while preserving that acceptance identity. Repeated
 * acknowledgement is idempotent so broker redelivery cannot move the recorded
 * dispatch boundary after it has been established.</p>
 */
public final class ConversionOutboxRecord {

    private final UUID outboxId;
    private final UUID jobId;
    private final long generation;
    private final Instant acceptedAt;
    private final Instant dispatchedAt;

    private ConversionOutboxRecord(
            UUID outboxId,
            UUID jobId,
            long generation,
            Instant acceptedAt,
            Instant dispatchedAt) {
        this.outboxId = outboxId;
        this.jobId = jobId;
        this.generation = generation;
        this.acceptedAt = acceptedAt;
        this.dispatchedAt = dispatchedAt;
    }

    /**
     * Creates a pending outbox record for one durably accepted job generation.
     *
     * @param outboxId unique durable dispatch identifier
     * @param jobId canonical accepted conversion-job identifier
     * @param generation positive lifecycle generation being dispatched
     * @param acceptedAt durable acceptance timestamp
     * @return pending outbox record
     * @throws NullPointerException when an identifier or timestamp is null
     * @throws IllegalArgumentException when {@code generation} is not positive
     */
    public static ConversionOutboxRecord pending(
            UUID outboxId,
            UUID jobId,
            long generation,
            Instant acceptedAt) {
        UUID requiredOutboxId = Objects.requireNonNull(outboxId, "outboxId");
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Instant requiredAcceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        return new ConversionOutboxRecord(
                requiredOutboxId,
                requiredJobId,
                generation,
                requiredAcceptedAt,
                null
        );
    }

    /**
     * Returns a terminal dispatch snapshot for this exact acceptance identity.
     *
     * <p>If this record was already dispatched, the current snapshot is
     * returned unchanged. This keeps repeated broker acknowledgements
     * idempotent.</p>
     *
     * @param dispatchedAt timestamp when dispatch authority was established
     * @return terminal dispatch snapshot, or this object when already dispatched
     * @throws NullPointerException when {@code dispatchedAt} is null
     * @throws IllegalArgumentException when dispatch predates durable acceptance
     */
    public ConversionOutboxRecord markDispatched(Instant dispatchedAt) {
        Instant requiredDispatchedAt = Objects.requireNonNull(dispatchedAt, "dispatchedAt");
        if (this.dispatchedAt != null) {
            return this;
        }
        if (requiredDispatchedAt.isBefore(acceptedAt)) {
            throw new IllegalArgumentException("dispatchedAt cannot predate acceptedAt");
        }
        return new ConversionOutboxRecord(
                outboxId,
                jobId,
                generation,
                acceptedAt,
                requiredDispatchedAt
        );
    }

    /**
     * Returns the unique durable dispatch identifier.
     *
     * @return outbox identifier
     */
    public UUID outboxId() {
        return outboxId;
    }

    /**
     * Returns the canonical conversion-job identifier.
     *
     * @return conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation authorized by this dispatch record.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns when durable acceptance authority was established.
     *
     * @return durable acceptance timestamp
     */
    public Instant acceptedAt() {
        return acceptedAt;
    }

    /**
     * Returns whether broker dispatch is still pending.
     *
     * @return true until the first dispatch acknowledgement is recorded
     */
    public boolean isPending() {
        return dispatchedAt == null;
    }

    /**
     * Returns the first broker dispatch acknowledgement when present.
     *
     * @return dispatch timestamp, or empty while pending
     */
    public Optional<Instant> dispatchedAt() {
        return Optional.ofNullable(dispatchedAt);
    }
}
