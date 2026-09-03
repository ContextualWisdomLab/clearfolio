package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextCoverageTest {
    @Test
    void testSanitizeCoverage() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, "\u0000");
        headers.add(TenantContext.SUBJECT_ID_HEADER, "\u0000abc\u0000");
        headers.add(TenantContext.PERMISSIONS_HEADER, "job:read");

        assertTrue(TenantContext.fromHeaders(headers).isEmpty()); // Because tenantId will be empty and become null

        TenantContext ctx = new TenantContext(null, null, null);
        assertNull(ctx.tenantId());
    }
}
