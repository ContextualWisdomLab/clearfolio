package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.add(TenantContext.TENANT_ID_HEADER, "\u0000valid\u0000tenant\u0000");
        validHeaders.add(TenantContext.SUBJECT_ID_HEADER, "\u0000abc\u0000");
        validHeaders.add(TenantContext.PERMISSIONS_HEADER, "\u0000job:read\u0000");
        TenantContext ctx2 = TenantContext.fromHeaders(validHeaders).orElseThrow();
        assertEquals("validtenant", ctx2.tenantId());
    }
}
