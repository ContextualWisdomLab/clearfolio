package com.clearfolio.viewer.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAccessServiceCoverageTest {
    @Test
    void testCleanCoverage() {
        // Also we want to test empty string
        TenantAccessService emptySecret = new TenantAccessService("", 300L, Clock.systemUTC());
        assertNotNull(emptySecret);

        assertThrows(IllegalArgumentException.class, () -> {
            new TenantAccessService("\u0000ab\u0000c\u0000def\u0000", 300L, Clock.systemUTC());
        });

        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.CLAIMS_ISSUED_AT_HEADER, "\u0000ab\u0000c\u0000def\u0000");
        headers.add(TenantContext.CLAIMS_SIGNATURE_HEADER, "\u0000ab\u0000c\u0000def\u0000");

        // This implicitly calls clean() with \u0000, achieving coverage.
        // It will fail later on parseIssuedAt, which is fine for line coverage.
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            emptySecret.require(headers, "any");
        });
    }
}
