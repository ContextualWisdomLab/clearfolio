package com.clearfolio.viewer.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Application service for document conversion job submission and lookup.
 */
public interface DocumentConversionService {
    /**
     * Submits an uploaded file for conversion.
     *
     * @param file uploaded file
     * @return conversion job identifier
     */
    UUID submit(MultipartFile file);

    /**
     * Submits an uploaded file for conversion with optional policy-override metadata.
     *
     * @param file uploaded file
     * @param overrideRequest policy-override request headers
     * @return conversion job identifier
     */
    default UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        return submit(file);
    }

    /**
     * Submits an uploaded file for conversion with policy and tenant metadata.
     *
     * @param file uploaded file
     * @param overrideRequest policy-override request headers
     * @param tenantContext tenant and subject claims for ownership metadata
     * @return conversion job identifier
     */
    default UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest, TenantContext tenantContext) {
        return submit(file, overrideRequest);
    }

    /**
     * Retrieves a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     * @return conversion job when found
     */
    Optional<ConversionJob> getJob(UUID jobId);

    /**
     * Retries a dead-lettered conversion job by moving it back to submitted state.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier that triggered the retry
     * @return retry outcome
     */
    RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId);

    /**
     * Retries only a dead-lettered job owned by the supplied tenant.
     *
     * <p>The compatibility default checks ownership before delegating to the
     * legacy mutation. Durable implementations should override this method with
     * one storage-scoped, generation-fenced transition.</p>
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier that triggered the retry
     * @param tenantContext authenticated tenant authority
     * @return retry outcome, with missing and foreign jobs both concealed as
     *         {@link RetryDeadLetterResult#NOT_FOUND}
     */
    default RetryDeadLetterResult retryDeadLettered(
            UUID jobId,
            String operatorId,
            TenantContext tenantContext
    ) {
        if (tenantContext == null) {
            return RetryDeadLetterResult.NOT_FOUND;
        }

        Optional<ConversionJob> job = getJob(jobId);
        if (job.isEmpty() || !job.get().belongsToTenant(tenantContext.tenantId())) {
            return RetryDeadLetterResult.NOT_FOUND;
        }

        return retryDeadLettered(jobId, operatorId);
    }

    /**
     * Deletes a conversion job owned by the supplied tenant context.
     *
     * @param jobId conversion job identifier
     * @param tenantContext tenant and subject claims for the delete request
     * @return true when an owned job was deleted; false when it was missing or
     *         belonged to another tenant
     */
    default boolean deleteJob(UUID jobId, TenantContext tenantContext) {
        if (tenantContext == null) {
            return false;
        }

        Optional<ConversionJob> job = getJob(jobId);
        if (job.isEmpty() || !job.get().belongsToTenant(tenantContext.tenantId())) {
            return false;
        }

        deleteJob(jobId);
        return true;
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     */
    void deleteJob(UUID jobId);

    /**
     * Returns all registered conversion jobs.
     *
     * @return an iterable of all conversion jobs
     */
    Iterable<ConversionJob> getAllJobs();

    /**
     * Returns only jobs owned by the supplied authenticated tenant.
     *
     * <p>The compatibility default prevents cross-tenant publication even when
     * an older adapter exposes only a global inventory. Durable implementations
     * should override this method with a tenant predicate at the storage query
     * boundary.</p>
     *
     * @param tenantContext authenticated tenant authority
     * @return immutable snapshot of tenant-owned jobs, or an empty result when
     *         tenant authority is absent
     */
    default Iterable<ConversionJob> getAllJobs(TenantContext tenantContext) {
        if (tenantContext == null) {
            return List.of();
        }

        List<ConversionJob> tenantJobs = new ArrayList<>();
        for (ConversionJob job : getAllJobs()) {
            if (job.belongsToTenant(tenantContext.tenantId())) {
                tenantJobs.add(job);
            }
        }
        return List.copyOf(tenantJobs);
    }
}
