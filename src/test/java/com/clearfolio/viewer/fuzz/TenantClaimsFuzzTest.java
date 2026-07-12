package com.clearfolio.viewer.fuzz;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;

/**
 * Fuzzes tenant-claim parsing from untrusted request headers.
 *
 * <p>{@link TenantContext#fromHeaders} normalizes the tenant/subject/permission
 * headers, and {@link TenantAccessService#require} additionally parses the
 * signed-claims {@code Issued-At} timestamp and compares an HMAC signature.
 * Invariants under test:
 * <ul>
 *   <li>{@code fromHeaders} never throws for any header combination; and</li>
 *   <li>{@code require} only ever fails with {@link ResponseStatusException}
 *       (never an unhandled {@link NumberFormatException} from the timestamp or
 *       any other runtime exception).</li>
 * </ul>
 */
final class TenantClaimsFuzzTest {

    private final TenantAccessService accessService =
            new TenantAccessService("clearfolio-fuzz-claims-secret", 300L);

    @FuzzTest(maxDuration = "60s")
    void headerClaimsParsingIsRobust(FuzzedDataProvider data) {
        HttpHeaders headers = new HttpHeaders();
        addFuzzedHeader(headers, TenantContext.TENANT_ID_HEADER, data);
        addFuzzedHeader(headers, TenantContext.SUBJECT_ID_HEADER, data);
        addFuzzedHeader(headers, TenantContext.PERMISSIONS_HEADER, data);
        addFuzzedHeader(headers, TenantContext.CLAIMS_ISSUED_AT_HEADER, data);
        addFuzzedHeader(headers, TenantContext.CLAIMS_SIGNATURE_HEADER, data);

        // Must never throw: pure normalization of arbitrary header values.
        Optional<TenantContext> context = TenantContext.fromHeaders(headers);
        if (context.isPresent()) {
            context.get().canonicalPermissions();
        }

        try {
            accessService.require(headers, TenantPermissions.JOB_CREATE);
        } catch (ResponseStatusException expected) {
            // Controlled 4xx rejection for missing/invalid/forged claims.
        }
    }

    private static void addFuzzedHeader(HttpHeaders headers, String name, FuzzedDataProvider data) {
        if (data.consumeBoolean()) {
            headers.add(name, data.consumeString(48));
        }
    }
}
