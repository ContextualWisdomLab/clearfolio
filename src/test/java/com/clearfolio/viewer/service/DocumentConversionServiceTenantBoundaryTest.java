package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.auth.TenantContext;

/**
 * Architecture regression for the application-service tenant boundary used by
 * administrative HTTP adapters.
 */
class DocumentConversionServiceTenantBoundaryTest {

    @Test
    void exposesTenantScopedListAndRetryContracts() {
        assertDoesNotThrow(
                () -> DocumentConversionService.class.getMethod(
                        "getJobsForTenant",
                        TenantContext.class
                ),
                "admin list behavior must not require an HTTP adapter to fetch the global job set"
        );
        assertDoesNotThrow(
                () -> DocumentConversionService.class.getMethod(
                        "retryDeadLettered",
                        UUID.class,
                        String.class,
                        TenantContext.class
                ),
                "retry ownership must be enforced by the application-service boundary"
        );
    }
}
