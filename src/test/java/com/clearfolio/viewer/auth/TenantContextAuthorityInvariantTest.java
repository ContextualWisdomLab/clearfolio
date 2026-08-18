package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Verifies that directly constructed and header-derived tenant contexts cannot
 * carry absent or control-corrupted authority across service boundaries.
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

    @Test
    void headerParsingRejectsControlCorruptedClaimsAndPermissions() {
        HttpHeaders controlTenant = headers("tenant\n-a", "subject-a", "job:read");
        HttpHeaders controlSubject = headers("tenant-a", "subject\u2028-a", "job:read");
        HttpHeaders controlPermission = headers("tenant-a", "subject-a", "job:\u0000read");

        assertTrue(TenantContext.fromHeaders(controlTenant).isEmpty());
        assertTrue(TenantContext.fromHeaders(controlSubject).isEmpty());
        assertTrue(TenantContext.fromHeaders(controlPermission).isEmpty());
    }

    private static HttpHeaders headers(String tenantId, String subjectId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, tenantId);
        headers.set(TenantContext.SUBJECT_ID_HEADER, subjectId);
        headers.set(TenantContext.PERMISSIONS_HEADER, permissions);
        return headers;
    }
}
