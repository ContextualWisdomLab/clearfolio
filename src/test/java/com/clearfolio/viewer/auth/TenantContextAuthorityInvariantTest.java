package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies that directly constructed tenant contexts cannot carry absent or
 * control-corrupted authority across internal service boundaries.
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
                () -> new TenantContext("   ", "subject-1", Set.of("job:read"))
        );

        assertEquals("tenantId is required", nullTenant.getMessage());
        assertEquals("tenantId is required", blankTenant.getMessage());
    }

    @Test
    void constructorRejectsControlCorruptedTenantAuthority() {
        for (String tenantId : List.of(
                "tenant\u0000-a",
                "tenant\n-a",
                "tenant\u001B-a",
                "tenant\u2028-a"
        )) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TenantContext(tenantId, "subject-1", Set.of("job:read"))
            );

            assertEquals("tenantId must not contain control characters", exception.getMessage());
        }
    }

    @Test
    void constructorRejectsMissingSubjectAuthority() {
        IllegalArgumentException nullSubject = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext("tenant-a", null, Set.of("job:read"))
        );
        IllegalArgumentException blankSubject = assertThrows(
                IllegalArgumentException.class,
                () -> new TenantContext("tenant-a", "   ", Set.of("job:read"))
        );

        assertEquals("subjectId is required", nullSubject.getMessage());
        assertEquals("subjectId is required", blankSubject.getMessage());
    }

    @Test
    void constructorRejectsControlCorruptedSubjectAuthority() {
        for (String subjectId : List.of(
                "subject\u0000-a",
                "subject\t-a",
                "subject\u007F-a",
                "subject\u2029-a"
        )) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TenantContext("tenant-a", subjectId, Set.of("job:read"))
            );

            assertEquals("subjectId must not contain control characters", exception.getMessage());
        }
    }
}
