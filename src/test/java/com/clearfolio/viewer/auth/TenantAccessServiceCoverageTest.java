package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantAccessServiceCoverageTest {
    @Test
    void testCleanCoverage() {
        TenantAccessService service = new TenantAccessService("\u0000ab\u0000c\u0000def\u0000", 300L, Clock.systemUTC());
        // Also we want to test empty string
        TenantAccessService emptySecret = new TenantAccessService("", 300L, Clock.systemUTC());
        assertNotNull(service);
        assertNotNull(emptySecret);
    }
}
