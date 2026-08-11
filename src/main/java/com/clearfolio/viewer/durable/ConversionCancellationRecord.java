package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable cancellation-race authority for one tenant-owned conversion-job
 * generation.
 *
 * <p>A record starts in {@link ConversionCancellationState#REQUESTED}. The
 * first terminal transition wins permanently: either cancellation takes effect
 * or the bound generation completes first. Repeating the winning transition is
 * idempotent, while attempting to rewrite a terminal winner fails closed.</p>
 */
public final class ConversionCancellationRecord {

    private static final int MAX_TENANT_ID_LENGTH = 256;

    private final UUID requestId;
    private final String tenantId;
    private final UUID jobId;
    private final long generation;
    private final Instant requestedAt;
    private final ConversionCancellationState state;
    private final Instant terminalAt;

    private ConversionCancellationRecord(
            UUID requestId,
            String tenantId,
            UUID jobId,
            long generation,
            Instant requestedAt,
            ConversionCancellationState state,
            Instant terminalAt) {
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.jobId = jobId;
        this.generation = generation;
        this.requestedAt = requestedAt;
        this.state = state;
        this.terminalAt = terminalAt;
    }

    /**
     * Creates a pending cancellation request for one exact tenant/job generation.
     *
     * @param requestId unique cancellation-request identifier
     * @param tenantId server-authoritative tenant identifier
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation being cancelled
     * @param requestedAt durable cancellation-request timestamp
     * @return immutable requested cancellation record
     * @throws NullPointerException when a required identifier or timestamp is null
     * @throws IllegalArgumentException when tenant authority or generation is invalid
     */
    public static ConversionCancellationRecord requested(
            UUID requestId,
            String tenantId,
            UUID jobId,
            long generation,
            Instant requestedAt) {
        UUID requiredRequestId = Objects.requireNonNull(requestId, "requestId");
        String normalizedTenantId = normalizeTenantId(Objects.requireNonNull(tenantId, "tenantId"));
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        Instant requiredRequestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        return new ConversionCancellationRecord(
                requiredRequestId,
                normalizedTenantId,
                requiredJobId,
                generation,
                requiredRequestedAt,
                ConversionCancellationState.REQUESTED,
                null
        );
    }

    /**
     * Records that cancellation won the race for this generation.
     *
     * @param cancelledAt timestamp when cancellation became terminal
     * @return terminal cancelled snapshot, or this object when already cancelled
     * @throws NullPointerException when {@code cancelledAt} is null
     * @throws IllegalArgumentException when the terminal timestamp predates the request
     * @throws IllegalStateException when completion already won the race
     */
    public ConversionCancellationRecord markCancelled(Instant cancelledAt) {
        Instant requiredCancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt");
        if (state == ConversionCancellationState.CANCELLED) {
            return this;
        }
        if (state != ConversionCancellationState.REQUESTED) {
            throw new IllegalStateException("completion already won cancellation race");
        }
        validateTerminalTimestamp(requiredCancelledAt);
        return terminal(ConversionCancellationState.CANCELLED, requiredCancelledAt);
    }

    /**
     * Records that job completion won before cancellation became effective.
     *
     * @param completedAt timestamp when completion became terminal
     * @return terminal completion-won snapshot, or this object when already completed
     * @throws NullPointerException when {@code completedAt} is null
     * @throws IllegalArgumentException when the terminal timestamp predates the request
     * @throws IllegalStateException when cancellation already won the race
     */
    public ConversionCancellationRecord markCompleted(Instant completedAt) {
        Instant requiredCompletedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (state == ConversionCancellationState.COMPLETED_BEFORE_CANCELLATION) {
            return this;
        }
        if (state != ConversionCancellationState.REQUESTED) {
            throw new IllegalStateException("cancellation already won race");
        }
        validateTerminalTimestamp(requiredCompletedAt);
        return terminal(ConversionCancellationState.COMPLETED_BEFORE_CANCELLATION, requiredCompletedAt);
    }

    /**
     * Checks whether caller authority identifies this exact cancellation generation.
     *
     * @param tenantId candidate tenant authority
     * @param jobId candidate conversion-job identifier
     * @param generation candidate lifecycle generation
     * @return true only for the exact non-null tenant/job/generation identity
     */
    public boolean authorizes(String tenantId, UUID jobId, long generation) {
        return tenantId != null
                && jobId != null
                && this.generation == generation
                && this.tenantId.equals(tenantId)
                && this.jobId.equals(jobId);
    }

    /**
     * Returns the unique cancellation-request identifier.
     *
     * @return cancellation-request identifier
     */
    public UUID requestId() {
        return requestId;
    }

    /**
     * Returns the normalized tenant authority bound to this request.
     *
     * @return tenant identifier
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Returns the permanently reserved conversion-job identifier.
     *
     * @return conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation fenced by this request.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns when cancellation was durably requested.
     *
     * @return cancellation-request timestamp
     */
    public Instant requestedAt() {
        return requestedAt;
    }

    /**
     * Returns the current monotonic cancellation state.
     *
     * @return cancellation state
     */
    public ConversionCancellationState state() {
        return state;
    }

    /**
     * Returns the winning terminal timestamp when the race has completed.
     *
     * @return terminal timestamp, or empty while cancellation is requested
     */
    public Optional<Instant> terminalAt() {
        return Optional.ofNullable(terminalAt);
    }

    private ConversionCancellationRecord terminal(
            ConversionCancellationState terminalState,
            Instant terminalAt) {
        return new ConversionCancellationRecord(
                requestId,
                tenantId,
                jobId,
                generation,
                requestedAt,
                terminalState,
                terminalAt
        );
    }

    private void validateTerminalTimestamp(Instant terminalTimestamp) {
        if (terminalTimestamp.isBefore(requestedAt)) {
            throw new IllegalArgumentException("terminal timestamp cannot predate cancellation request");
        }
    }

    private static String normalizeTenantId(String tenantId) {
        String normalized = tenantId.replace("\u0000", "").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (normalized.length() > MAX_TENANT_ID_LENGTH) {
            throw new IllegalArgumentException("tenantId must not exceed 256 characters");
        }
        return normalized;
    }
}
