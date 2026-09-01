package com.clearfolio.viewer.repository;

/**
 * Result of an atomic tenant-scoped dead-letter retry transition.
 */
public enum TenantScopedRetryResult {
    /** Target is absent or does not belong to the authenticated tenant. */
    NOT_FOUND,
    /** Target exists for the tenant but is not currently retryable. */
    NOT_ELIGIBLE,
    /** Tenant-owned target was atomically moved back to submitted state. */
    ACCEPTED
}
