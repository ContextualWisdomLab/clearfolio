package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * Persistence abstraction for conversion jobs.
 */
public interface ConversionJobRepository {

    /**
     * Result of an atomic find-or-store operation by content hash.
     *
     * @param canonicalJob canonical stored conversion job
     * @param created true when the candidate was newly stored
     */
    record FindOrStoreResult(ConversionJob canonicalJob, boolean created) {
    }

    /**
     * Saves a conversion job.
     *
     * @param job conversion job to store
     * @return stored conversion job
     */
    ConversionJob save(ConversionJob job);

    /**
     * Finds a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     * @return matching conversion job when found
     */
    Optional<ConversionJob> findById(UUID jobId);

    /**
     * Finds a demo-tenant conversion job by uploaded file content hash.
     *
     * @param contentHash uploaded file content hash
     * @return matching conversion job when found
     */
    Optional<ConversionJob> findByContentHash(String contentHash);

    /**
     * Finds a conversion job by tenant and uploaded file content hash.
     *
     * @param tenantId tenant identifier
     * @param contentHash uploaded file content hash
     * @return matching conversion job when found
     */
    default Optional<ConversionJob> findByTenantAndContentHash(String tenantId, String contentHash) {
        return findByContentHash(contentHash).filter(job -> job.belongsToTenant(tenantId));
    }

    /**
     * Finds a conversion job by tenant and identifier.
     *
     * @param tenantId tenant identifier
     * @param jobId conversion job identifier
     * @return matching conversion job when found and owned by the tenant
     */
    default Optional<ConversionJob> findByTenantAndId(String tenantId, UUID jobId) {
        return findById(jobId).filter(job -> job.belongsToTenant(tenantId));
    }

    /**
     * Returns a snapshot of all known conversion jobs.
     *
     * @return current conversion jobs
     */
    List<ConversionJob> findAll();

    /**
     * Finds jobs that should be considered for recovery after worker restart.
     *
     * @param now timestamp used to evaluate due submitted jobs
     * @param staleProcessingBefore processing jobs started before this instant
     *        are considered stale
     * @return recoverable conversion jobs
     */
    default List<ConversionJob> findRecoverableJobs(Instant now, Instant staleProcessingBefore) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(staleProcessingBefore, "staleProcessingBefore");
        return findAll().stream()
                .filter(job -> job.isReadyForProcessing(now) || isStaleProcessing(job, staleProcessingBefore))
                .toList();
    }

    /**
     * Stores a new job or returns the existing canonical job for the same tenant
     * and hash.
     *
     * @param candidate candidate conversion job
     * @return canonical stored conversion job and whether the candidate was created
     */
    FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate);

    /**
     * Deletes a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     */
    void deleteById(UUID jobId);

    private static boolean isStaleProcessing(ConversionJob job, Instant staleProcessingBefore) {
        Instant startedAt = job.getStartedAt();
        return job.getStatus() == ConversionJobStatus.PROCESSING
                && startedAt != null
                && startedAt.isBefore(staleProcessingBefore)
                && job.canRetry();
    }
}
