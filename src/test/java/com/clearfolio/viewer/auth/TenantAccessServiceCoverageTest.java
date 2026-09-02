package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TenantAccessServiceCoverageTest {
    @Test
    void testCleanCoverage() {
        TenantAccessService service = new TenantAccessService("\u0000abc\u0000def", 300L, Clock.systemUTC());
        // Also we want to test empty string
        TenantAccessService emptySecret = new TenantAccessService("", 300L, Clock.systemUTC());
    }
}
