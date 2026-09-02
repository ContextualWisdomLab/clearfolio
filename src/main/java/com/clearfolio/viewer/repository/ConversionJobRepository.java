package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * Persistence port for conversion-job aggregate state.
 *
 * <p>Tenant-aware application operations should use tenant-scoped repository
 * methods so foreign rows are filtered at the persistence boundary whenever the
 * concrete adapter can do so natively. Default implementations preserve current
 * in-memory/legacy adapters without changing tenant semantics.</p>
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
     * Finds a conversion job by identifier for trusted internal paths.
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
     * <p>Concrete durable adapters should implement this predicate in their
     * storage query. The compatibility default filters the single retrieved
     * aggregate and never changes not-found versus cross-tenant semantics.</p>
     *
     * @param tenantId tenant identifier
     * @param jobId conversion job identifier
     * @return matching conversion job when found and owned by the tenant
     */
    default Optional<ConversionJob> findByTenantAndId(String tenantId, UUID jobId) {
        return findById(jobId).filter(job -> job.belongsToTenant(tenantId));
    }

    /**
     * Returns a snapshot of all known conversion jobs for trusted internal and
     * recovery behavior.
     *
     * @return current conversion jobs
     */
    List<ConversionJob> findAll();

    /**
     * Returns only conversion jobs owned by the supplied tenant.
     *
     * <p>Durable adapters should push this predicate into SQL or their native
     * storage query. The compatibility default is intentionally correct before
     * it is optimal and prevents tenant filtering from being duplicated in
     * delivery adapters.</p>
     *
     * @param tenantId tenant identifier
     * @return current conversion jobs owned by the tenant
     */
    default List<ConversionJob> findAllByTenant(String tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return findAll().stream()
                .filter(job -> job.belongsToTenant(tenantId))
                .toList();
    }

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
     * Deletes a conversion job by identifier for a caller that has already
     * established aggregate ownership.
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
