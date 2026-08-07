package com.clearfolio.viewer.service;

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
     * Retries a dead-lettered conversion job owned by the supplied tenant.
     *
     * <p>The default fails closed without reading a job or invoking the legacy
     * unscoped retry method. A concrete service must override this method and
     * enforce tenant selection and the retry transition within one persistence
     * boundary before an administrative caller can receive an accepted result.</p>
     *
     * @param jobId conversion job identifier
     * @param tenantContext tenant and subject claims for the retry request
     * @param operatorId privacy-safe operator fingerprint that triggered retry
     * @return not-found until the implementation supplies an atomic tenant-aware
     *         retry operation
     */
    default RetryDeadLetterResult retryDeadLettered(
            UUID jobId,
            TenantContext tenantContext,
            String operatorId
    ) {
        return RetryDeadLetterResult.NOT_FOUND;
    }

    /**
     * Deletes a conversion job owned by the supplied tenant context.
     *
     * <p>The default fails closed without reading a job or invoking the legacy
     * unscoped delete method. A concrete service must override this method and
     * enforce tenant selection and deletion within one persistence boundary.</p>
     *
     * @param jobId conversion job identifier
     * @param tenantContext tenant and subject claims for the delete request
     * @return false until the implementation supplies an atomic tenant-aware
     *         delete operation
     */
    default boolean deleteJob(UUID jobId, TenantContext tenantContext) {
        return false;
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     */
    void deleteJob(UUID jobId);

    /**
     * Returns only jobs visible to the authenticated tenant context.
     *
     * <p>The default is intentionally empty. Concrete production services must
     * override this method and delegate to a repository query that applies the
     * tenant predicate before job objects cross the persistence boundary.</p>
     *
     * @param tenantContext authenticated tenant and subject claims
     * @return tenant-owned jobs, or an empty iterable when scoped listing is not
     *         implemented or the context is absent
     */
    default Iterable<ConversionJob> getJobsForTenant(TenantContext tenantContext) {
        return List.of();
    }

    /**
     * Returns all registered conversion jobs.
     *
     * @return an iterable of all conversion jobs
     */
    Iterable<ConversionJob> getAllJobs();
}
