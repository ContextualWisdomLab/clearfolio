package com.clearfolio.viewer.durable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable durable evidence that one broker message was first received for an
 * exact conversion-job generation.
 *
 * <p>A durable inbox adapter can persist this receipt under {@link #messageId}
 * and treat an exact later delivery as an idempotent redelivery. Reusing the
 * same message identity for another job or generation does not authorize work,
 * preventing stale or misrouted broker deliveries from crossing lifecycle
 * authority boundaries.</p>
 */
public final class ConversionDeliveryReceipt {

    private final UUID messageId;
    private final UUID jobId;
    private final long generation;
    private final Instant firstReceivedAt;

    /**
     * Creates immutable evidence for the first accepted observation of one
     * broker message.
     *
     * @param messageId stable broker or adapter message identity used for deduplication
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation carried by the message
     * @param firstReceivedAt persisted instant when the message was first observed
     * @throws NullPointerException when an identity or timestamp is null
     * @throws IllegalArgumentException when {@code generation} is not positive
     */
    public ConversionDeliveryReceipt(
            UUID messageId,
            UUID jobId,
            long generation,
            Instant firstReceivedAt) {
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        this.generation = generation;
        this.firstReceivedAt = Objects.requireNonNull(firstReceivedAt, "firstReceivedAt");
    }

    /**
     * Checks whether a candidate delivery is the exact redelivery authorized by
     * this persisted receipt.
     *
     * @param candidateMessageId candidate stable broker message identity
     * @param candidateJobId candidate permanently reserved job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @return true only when message, job, and generation all match this receipt;
     *         false for null or mismatched candidate identities
     */
    public boolean authorizes(
            UUID candidateMessageId,
            UUID candidateJobId,
            long candidateGeneration) {
        return messageId.equals(candidateMessageId)
                && jobId.equals(candidateJobId)
                && generation == candidateGeneration;
    }

    /**
     * Returns the stable broker or adapter message identity.
     *
     * @return message identity used for duplicate-delivery suppression
     */
    public UUID messageId() {
        return messageId;
    }

    /**
     * Returns the permanently reserved conversion-job identifier.
     *
     * @return job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation carried by this delivery.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns when this message was first durably observed.
     *
     * @return first receipt timestamp
     */
    public Instant firstReceivedAt() {
        return firstReceivedAt;
    }
}
