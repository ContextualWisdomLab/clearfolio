package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for the server-owned external-role to Clearfolio-permission mapping boundary.
 */
class TenantRoleMappingPolicyTest {

    @Test
    void resolvesOnlyServerMappedPermissionsIntoFixedTenantAuthority() {
        Map<String, Set<String>> rolePermissions = new LinkedHashMap<>();
        Set<String> readerPermissions = new LinkedHashSet<>(Set.of(
                TenantPermissions.JOB_READ,
                TenantPermissions.VIEWER_READ
        ));
        rolePermissions.put("document-reader", readerPermissions);
        rolePermissions.put("document-uploader", Set.of(TenantPermissions.JOB_CREATE));

        TenantRoleMappingPolicy policy = new TenantRoleMappingPolicy("tenant-a", rolePermissions);
        readerPermissions.add(TenantPermissions.JOB_DELETE);
        rolePermissions.put("unexpected-admin", Set.of(TenantPermissions.JOB_DELETE));

        Optional<TenantContext> resolved = policy.resolve(
                "employee-007",
                Set.of("document-reader", "document-uploader", "token-supplied-permission")
        );

        assertTrue(resolved.isPresent());
        assertEquals("tenant-a", resolved.get().tenantId());
        assertEquals("employee-007", resolved.get().subjectId());
        assertEquals(
                Set.of(TenantPermissions.JOB_READ, TenantPermissions.VIEWER_READ, TenantPermissions.JOB_CREATE),
                resolved.get().permissions()
        );
        assertFalse(resolved.get().permissions().contains(TenantPermissions.JOB_DELETE));
        assertFalse(policy.rolePermissions().containsKey("unexpected-admin"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> policy.rolePermissions().put("admin", Set.of(TenantPermissions.JOB_DELETE))
        );
    }

    @Test
    void failsClosedWhenSubjectOrRolesDoNotMap() {
        TenantRoleMappingPolicy policy = new TenantRoleMappingPolicy(
                "tenant-a",
                Map.of("reader", Set.of(TenantPermissions.JOB_READ))
        );

        assertTrue(policy.resolve("employee-007", Set.of("reader")).isPresent());
        assertTrue(policy.resolve("employee-007", Set.of("reader", "unknown")).isPresent());
        assertTrue(policy.resolve("employee-007", Set.of("unknown")).isEmpty());
        assertTrue(policy.resolve("employee-007", Set.of()).isEmpty());
        assertTrue(policy.resolve("employee-007", null).isEmpty());
        assertTrue(policy.resolve(null, Set.of("reader")).isEmpty());
        assertTrue(policy.resolve(" ", Set.of("reader")).isEmpty());
        assertTrue(policy.resolve(" employee-007 ", Set.of("reader")).isEmpty());
        assertTrue(policy.resolve("employee\u0000-007", Set.of("reader")).isEmpty());
    }

    @Test
    void rejectsAmbiguousServerOwnedMappingAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy(null, Map.of(
                "reader", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy(" ", Map.of(
                "reader", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy(" tenant-a ", Map.of(
                "reader", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant\u0000-a", Map.of(
                "reader", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", null));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of()));

        Map<String, Set<String>> nullRole = new LinkedHashMap<>();
        nullRole.put(null, Set.of(TenantPermissions.JOB_READ));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", nullRole));

        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                " ", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                " reader ", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader\u0000", Set.of(TenantPermissions.JOB_READ)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader", Set.of()
        )));

        Map<String, Set<String>> nullPermissions = new LinkedHashMap<>();
        nullPermissions.put("reader", null);
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", nullPermissions));

        Set<String> nullPermission = new LinkedHashSet<>();
        nullPermission.add(TenantPermissions.JOB_READ);
        nullPermission.add(null);
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader", nullPermission
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader", Set.of(" ")
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader", Set.of(" job:read ")
        )));
        assertThrows(IllegalArgumentException.class, () -> new TenantRoleMappingPolicy("tenant-a", Map.of(
                "reader", Set.of("job:\u0000read")
        )));
    }
}
