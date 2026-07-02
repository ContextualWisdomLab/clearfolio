package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void hasPermissionReturnsFalseForMissingPermission() {
        TenantContext context = new TenantContext("tenant-a", "user-1", java.util.Set.of(TenantPermissions.JOB_READ));

        assertFalse(context.hasPermission(TenantPermissions.JOB_RETRY));
    }
}
