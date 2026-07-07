package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TenantContextTest {

    @Test
    void fromHeadersParsesRequiredClaimsAndPermissions() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, " tenant-a ");
        headers.add(TenantContext.SUBJECT_ID_HEADER, " user-1 ");
        headers.add(TenantContext.PERMISSIONS_HEADER, "job:read, viewer:read,job:read");

        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();

        assertEquals("tenant-a", context.tenantId());
        assertEquals("user-1", context.subjectId());
        assertTrue(context.hasPermission(TenantPermissions.JOB_READ));
        assertTrue(context.hasPermission(TenantPermissions.VIEWER_READ));
        assertEquals(2, context.permissions().size());
    }

    @Test
    void fromHeadersReturnsEmptyWhenRequiredClaimIsMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.add(TenantContext.SUBJECT_ID_HEADER, "user-1");

        assertTrue(TenantContext.fromHeaders(headers).isEmpty());
    }

    @Test
    void fromHeadersReturnsEmptyWhenHeadersAreNull() {
        assertTrue(TenantContext.fromHeaders(null).isEmpty());
    }

    @Test
    void fromHeadersReturnsEmptyWhenTenantOrSubjectIsBlank() {
        HttpHeaders blankTenant = new HttpHeaders();
        blankTenant.add(TenantContext.TENANT_ID_HEADER, " \u0000 ");
        blankTenant.add(TenantContext.SUBJECT_ID_HEADER, "user-1");
        blankTenant.add(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);

        HttpHeaders blankSubject = new HttpHeaders();
        blankSubject.add(TenantContext.TENANT_ID_HEADER, "tenant-a");
        blankSubject.add(TenantContext.SUBJECT_ID_HEADER, " ");
        blankSubject.add(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);

        assertTrue(TenantContext.fromHeaders(blankTenant).isEmpty());
        assertTrue(TenantContext.fromHeaders(blankSubject).isEmpty());
    }

    @Test
    void constructorUsesEmptyPermissionSetWhenPermissionsAreNull() {
        TenantContext context = new TenantContext("tenant-a", "user-1", null);

        assertTrue(context.permissions().isEmpty());
    }

    @Test
    void permissionParserDropsBlankEntries() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.add(TenantContext.SUBJECT_ID_HEADER, "user-1");
        headers.add(TenantContext.PERMISSIONS_HEADER, "job:read, ,\u0000,viewer:read");

        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();

        assertEquals(Set.of(TenantPermissions.JOB_READ, TenantPermissions.VIEWER_READ), context.permissions());
    }

    @Test
    void hasPermissionReturnsFalseForMissingPermission() {
        TenantContext context = new TenantContext("tenant-a", "user-1", java.util.Set.of(TenantPermissions.JOB_READ));

        assertFalse(context.hasPermission(TenantPermissions.JOB_RETRY));
    }

    @Test
    void canonicalPermissionsReturnsStablePermissionClaimString() {
        TenantContext context = new TenantContext(
                "tenant-a",
                "user-1",
                new LinkedHashSet<>(List.of(TenantPermissions.JOB_READ, TenantPermissions.VIEWER_READ))
        );

        assertEquals("job:read,viewer:read", context.canonicalPermissions());
    }
}
