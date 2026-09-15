package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextCoverageTest {
    @Test
    void permissionParserHandlesEmptyPermissionsHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.add(TenantContext.SUBJECT_ID_HEADER, "user-1");
        headers.add(TenantContext.PERMISSIONS_HEADER, "  ");
        assertTrue(TenantContext.fromHeaders(headers).isEmpty());
    }
}
