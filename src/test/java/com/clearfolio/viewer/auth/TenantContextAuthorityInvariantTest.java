package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies that directly constructed tenant contexts cannot carry absent
 * authority across internal service boundaries.
 */
class TenantContextAuthorityInvariantTest {

    @Test
    void constructorRejectsMissingTenantAuthority() {
        IllegalArgumentException nullTenant = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext(null, "subject-1", Set.of("job:read"))
        );
        IllegalArgumentException blankTenant = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext(" \u0000 ", "subject-1", Set.of("job:read"))
        );

        assertEquals("tenantId is required", nullTenant.getMessage());
        assertEquals("tenantId is required", blankTenant.getMessage());
    }

    @Test
    void constructorRejectsMissingSubjectAuthority() {
        IllegalArgumentException nullSubject = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext("tenant-a", null, Set.of("job:read"))
        );
        IllegalArgumentException blankSubject = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext("tenant-a", "\u0000   ", Set.of("job:read"))
        );

        assertEquals("subjectId is required", nullSubject.getMessage());
        assertEquals("subjectId is required", blankSubject.getMessage());
    }
}
