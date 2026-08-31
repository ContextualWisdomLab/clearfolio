package com.clearfolio.viewer.service;

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
     * Retries a dead-lettered job through a tenant-bound atomic mutation.
     *
     * @param jobId conversion job identifier
     * @param tenantContext authenticated tenant context
     * @param operatorId pseudonymous operator identifier that triggered the retry
     * @return retry outcome; cross-tenant and missing targets are not found
     */
    RetryDeadLetterResult retryDeadLettered(
            UUID jobId,
            TenantContext tenantContext,
            String operatorId
    );

    /**
     * Deletes a conversion job through a tenant-bound atomic mutation.
     *
     * @param jobId conversion job identifier
     * @param tenantContext authenticated tenant context
     * @return true only when the current tenant-owned job was deleted
     */
    boolean deleteJob(UUID jobId, TenantContext tenantContext);

    /**
     * Deletes a conversion job without a tenant boundary.
     *
     * <p>This method is retained for trusted internal compatibility. Request-facing
     * administrator paths must use {@link #deleteJob(UUID, TenantContext)}.
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
}
