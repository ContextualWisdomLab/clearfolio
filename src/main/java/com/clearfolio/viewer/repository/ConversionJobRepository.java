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
 *
 * <p>Implementations must treat each conversion-job identifier as an immutable
 * lifecycle identity. Once an identifier has been accepted for one job, a
 * distinct job must never be stored under that identifier, including after
 * deletion. This prevents an asynchronous UUID-only handoff from resolving to
 * another tenant or lifecycle generation. Durable implementations should keep
 * an equivalent tombstone or generation reservation.</p>
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
     * Saves a conversion job without rebinding a live or previously used
     * identifier to a distinct job.
     *
     * <p>Saving the exact same live object may be idempotent. Any distinct job
     * with the same current or tombstoned identifier must fail closed.</p>
     *
     * @param job conversion job to store
     * @return stored conversion job
     * @throws IllegalStateException when the identifier is already owned or reserved
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
     * <p>The default deliberately returns an empty result rather than calling
     * {@link #findByContentHash(String)} and filtering after a global lookup.
     * Durable adapters must override this method with both tenant and content
     * hash predicates in the storage query.</p>
     *
     * @param tenantId authenticated tenant identifier
     * @param contentHash uploaded file content hash
     * @return matching tenant-owned job, or an empty result until the adapter
     *         implements the scoped lookup
     */
    default Optional<ConversionJob> findByTenantAndContentHash(String tenantId, String contentHash) {
        return Optional.empty();
    }

    /**
     * Finds a conversion job by tenant and identifier.
     *
     * <p>The default deliberately returns an empty result rather than calling
     * {@link #findById(UUID)} and filtering after a global object lookup.
     * Durable adapters must override this method with a tenant predicate in the
     * storage query before administrative callers may receive a job object.</p>
     *
     * @param tenantId authenticated tenant identifier
     * @param jobId conversion job identifier
     * @return matching tenant-owned job, or an empty result until the adapter
     *         implements the scoped lookup
     */
    default Optional<ConversionJob> findByTenantAndId(String tenantId, UUID jobId) {
        return Optional.empty();
    }

    /**
     * Returns a snapshot of all known conversion jobs.
     *
     * @return current conversion jobs
     */
    List<ConversionJob> findAll();

    /**
     * Returns only jobs owned by the supplied tenant.
     *
     * <p>The default deliberately returns an empty result rather than falling
     * back to {@link #findAll()}. Every durable repository adapter must
     * implement a tenant predicate in its storage query before administrative
     * callers may receive job objects.</p>
     *
     * @param tenantId authenticated tenant identifier
     * @return tenant-owned jobs, or an empty list until the adapter implements
     *         the scoped query
     */
    default List<ConversionJob> findAllByTenantId(String tenantId) {
        return List.of();
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
     * <p>The tenant-and-hash lookup and identifier reservation must occur in one
     * storage transaction or equivalent critical section. A candidate whose
     * identifier is already live or tombstoned for another object must fail
     * closed instead of replacing that object.</p>
     *
     * @param candidate candidate conversion job
     * @return canonical stored conversion job and whether the candidate was created
     */
    FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate);

    /**
     * Deletes one job only when it is owned by the supplied tenant.
     *
     * <p>The default fails closed without calling the global delete method.
     * Durable repository adapters must override this method with one atomic
     * tenant-predicate delete operation. Successful deletion must not release
     * the identifier for reuse.</p>
     *
     * @param tenantId authenticated tenant identifier
     * @param jobId conversion job identifier
     * @return true only when an owned job was deleted
     */
    default boolean deleteByTenantAndId(String tenantId, UUID jobId) {
        return false;
    }

    /**
     * Deletes a conversion job by identifier.
     *
     * <p>The identifier remains reserved after deletion.</p>
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
