package com.clearfolio.viewer.repository;

import java.util.UUID;

/**
 * Repository capability for tenant-bound destructive job mutations.
 *
 * <p>Implementations must validate tenant ownership and remove the matching
 * job in one atomic operation. Callers must fail closed when this capability
 * is unavailable rather than falling back to an identifier-only mutation.
 */
public interface TenantScopedJobMutationRepository {

    /**
     * Deletes a job only when the current record belongs to the supplied tenant.
     *
     * @param tenantId authenticated tenant identifier
     * @param jobId conversion job identifier
     * @return true only when the tenant-owned record was atomically removed
     */
    boolean deleteByTenantAndId(String tenantId, UUID jobId);
}
