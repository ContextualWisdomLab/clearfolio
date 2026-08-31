package com.clearfolio.viewer.repository;

import java.util.UUID;

/**
 * Lifecycle-store capability for tenant-bound administrative retry transitions.
 *
 * <p>Implementations must evaluate ownership and transition the current job in
 * one atomic operation so an identifier cannot be rebound between authorization
 * and mutation.
 */
public interface TenantScopedConversionJobStateStore {

    /**
     * Retries a dead-lettered job only for its current owning tenant.
     *
     * @param jobId conversion job identifier
     * @param tenantId authenticated tenant identifier
     * @param operatorId pseudonymous operator identifier
     * @return atomic tenant-scoped retry outcome
     */
    TenantScopedRetryResult retryDeadLettered(
            UUID jobId,
            String tenantId,
            String operatorId
    );
}
