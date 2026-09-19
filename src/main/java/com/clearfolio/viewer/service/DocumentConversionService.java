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
     * @return matching conversion job when found
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
     * Retries a dead-lettered job only when it belongs to the supplied tenant.
     * Missing and foreign-tenant identifiers intentionally share NOT_FOUND so the
     * caller cannot use this boundary as a cross-tenant existence oracle.
     *
     * <p>Repository-backed implementations should override this method so the
     * tenant predicate and state transition execute in one application-service
     * operation.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier that triggered the retry
     * @param tenantContext verified tenant and subject claims
     * @return retry outcome
     */
    default RetryDeadLetterResult retryDeadLettered(
            UUID jobId,
            String operatorId,
            TenantContext tenantContext) {
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
     * Returns only conversion jobs owned by the supplied tenant.
     *
     * <p>The default implementation preserves compatibility with repositories
     * that only expose a global snapshot. Durable repository implementations
     * should override this contract with a tenant-predicate query rather than
     * loading all tenants into memory.
     *
     * @param tenantContext verified tenant and subject claims
     * @return immutable snapshot of jobs owned by the tenant
     */
    default Iterable<ConversionJob> getAllJobs(TenantContext tenantContext) {
        if (tenantContext == null) {
            return List.of();
        }

        List<ConversionJob> ownedJobs = new ArrayList<>();
        for (ConversionJob job : getAllJobs()) {
            if (job.belongsToTenant(tenantContext.tenantId())) {
                ownedJobs.add(job);
            }
        }
        return List.copyOf(ownedJobs);
    }
}
