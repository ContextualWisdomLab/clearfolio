package com.clearfolio.viewer.service;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Application-service port for conversion submission, observation, and lifecycle
 * commands.
 *
 * <p>Tenant-facing adapters should use the tenant-scoped query and mutation
 * methods so ownership is enforced inside the application boundary rather than
 * reconstructed independently by HTTP, CLI, or future messaging adapters. The
 * unscoped methods remain lower-level compatibility contracts for internal
 * worker/recovery paths and legacy callers.</p>
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
     * Retrieves a conversion job by identifier for internal compatibility paths.
     *
     * <p>Tenant-facing adapters must prefer tenant-scoped operations so a raw
     * cross-tenant entity does not cross the application boundary before access
     * control is evaluated.</p>
     *
     * @param jobId conversion job identifier
     * @return conversion job when found
     */
    Optional<ConversionJob> getJob(UUID jobId);

    /**
     * Returns jobs owned by the supplied verified tenant context.
     *
     * <p>The default implementation preserves compatibility for repository
     * adapters that have not yet added a tenant-native query. Production
     * implementations should override this method and push the tenant predicate
     * into their persistence port so foreign tenant rows are not materialized
     * unnecessarily.</p>
     *
     * @param tenantContext verified tenant and subject claims
     * @return snapshot containing only jobs owned by that tenant; an empty
     *         snapshot when the tenant context is absent
     */
    default Iterable<ConversionJob> getJobsForTenant(TenantContext tenantContext) {
        if (tenantContext == null) {
            return java.util.List.of();
        }
        return StreamSupport.stream(getAllJobs().spliterator(), false)
                .filter(job -> job.belongsToTenant(tenantContext.tenantId()))
                .toList();
    }

    /**
     * Retries a dead-lettered conversion job by moving it back to submitted state.
     *
     * <p>This unscoped compatibility contract is intended for trusted internal
     * paths that already own resource authority. Tenant-facing adapters must use
     * the tenant-scoped overload.</p>
     *
     * @param jobId conversion job identifier
     * @param operatorId privacy-safe operator audit identifier
     * @return retry outcome
     */
    RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId);

    /**
     * Retries a dead-lettered job only when it is owned by the supplied tenant.
     *
     * <p>Missing and cross-tenant identifiers intentionally collapse to
     * {@link RetryDeadLetterResult#NOT_FOUND}; callers therefore do not gain a
     * resource-existence oracle. The privacy-safe operator identifier is audit
     * metadata only and does not participate in authorization.</p>
     *
     * @param jobId conversion job identifier
     * @param operatorId privacy-safe operator audit identifier
     * @param tenantContext verified tenant and subject claims
     * @return retry outcome with cross-tenant resources hidden as not found
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
     * <p>Missing and cross-tenant identifiers return {@code false} so external
     * adapters can map both conditions to the same not-found response without
     * exposing resource existence.</p>
     *
     * @param jobId conversion job identifier
     * @param tenantContext verified tenant and subject claims for the delete request
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
     * Deletes a conversion job by identifier for trusted internal compatibility
     * paths.
     *
     * @param jobId conversion job identifier
     */
    void deleteJob(UUID jobId);

    /**
     * Returns all registered conversion jobs for trusted internal/recovery paths.
     *
     * <p>Tenant-facing adapters must not expose this global snapshot. They should
     * call {@link #getJobsForTenant(TenantContext)} instead.</p>
     *
     * @return an iterable of all conversion jobs
     */
    Iterable<ConversionJob> getAllJobs();
}
