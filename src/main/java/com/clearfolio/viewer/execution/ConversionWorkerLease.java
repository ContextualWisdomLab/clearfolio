package com.clearfolio.viewer.execution;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable authority token for one conversion-worker claim.
 *
 * <p>The lease binds a worker to one permanently identified conversion job,
 * one positive lifecycle generation, and one unique lease identifier. A later
 * publication boundary can therefore reject work produced by an expired or
 * superseded claim instead of trusting job identity alone.</p>
 */
public final class ConversionWorkerLease {

    private final UUID jobId;
    private final long generation;
    private final UUID leaseId;

    private ConversionWorkerLease(UUID jobId, long generation, UUID leaseId) {
        this.jobId = jobId;
        this.generation = generation;
        this.leaseId = leaseId;
    }

    /**
     * Issues a lease identity after validating all authority components.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param generation positive lifecycle generation owned by this claim
     * @param leaseId unique identifier for this exact worker claim
     * @return immutable worker lease
     * @throws NullPointerException when {@code jobId} or {@code leaseId} is null
     * @throws IllegalArgumentException when {@code generation} is not positive
     */
    public static ConversionWorkerLease issue(UUID jobId, long generation, UUID leaseId) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        UUID requiredLeaseId = Objects.requireNonNull(leaseId, "leaseId");
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        return new ConversionWorkerLease(requiredJobId, generation, requiredLeaseId);
    }

    /**
     * Returns the conversion job whose work this lease may authorize.
     *
     * @return permanently reserved conversion-job identifier
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Returns the lifecycle generation fenced by this lease.
     *
     * @return positive lifecycle generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the unique identifier for this exact worker claim.
     *
     * @return worker lease identifier
     */
    public UUID leaseId() {
        return leaseId;
    }

    /**
     * Checks whether candidate publication authority exactly matches this lease.
     *
     * @param candidateJobId candidate permanently reserved job identifier
     * @param candidateGeneration candidate lifecycle generation
     * @param candidateLeaseId candidate worker lease identifier
     * @return true only when all three authority components exactly match
     */
    public boolean authorizes(
            UUID candidateJobId,
            long candidateGeneration,
            UUID candidateLeaseId) {
        return jobId.equals(candidateJobId)
                && generation == candidateGeneration
                && leaseId.equals(candidateLeaseId);
    }
}
