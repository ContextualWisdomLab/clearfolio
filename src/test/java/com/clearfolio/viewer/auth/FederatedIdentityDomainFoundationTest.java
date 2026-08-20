package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FederatedIdentityDomainFoundationTest {

    @Test
    void serverOwnedTrustKeyTimeAndRolePoliciesProduceOneTenantContext() {
        Instant fetchedAt = Instant.parse("2026-08-20T14:00:00Z");
        Instant expiresAt = fetchedAt.plusSeconds(300);
        Instant verifiedAt = fetchedAt.plusSeconds(60);

        IssuerTrustPolicy trustPolicy = new IssuerTrustPolicy(
                "enterprise-primary",
                "https://identity.example.com/tenant-a",
                "clearfolio-api",
                Set.of("RS256")
        );
        IdentityKeySnapshot keySnapshot = new IdentityKeySnapshot(
                trustPolicy.profileId(),
                4L,
                fetchedAt,
                expiresAt,
                Set.of("key-2026-08")
        );
        FederatedTokenTimeWindow tokenWindow = new FederatedTokenTimeWindow(
                fetchedAt.minusSeconds(30),
                expiresAt.minusSeconds(30)
        );
        TenantRoleMappingPolicy roleMapping = new TenantRoleMappingPolicy(
                "tenant-a",
                Map.of(
                        "document-reader",
                        Set.of(TenantPermissions.JOB_READ, TenantPermissions.VIEWER_READ),
                        "document-uploader",
                        Set.of(TenantPermissions.JOB_CREATE)
                )
        );
        TenantContext expected = roleMapping.resolve(
                "subject-a",
                Set.of("document-reader", "untrusted-token-permission")
        ).orElseThrow();

        IdentityVerifier verifier = (profileId, bearerToken) -> {
            assertEquals(trustPolicy.profileId(), profileId);
            assertEquals("opaque-bearer-token", bearerToken);
            assertTrue(trustPolicy.allowsAlgorithm("RS256"));
            assertTrue(keySnapshot.authorizes("key-2026-08", 4L, verifiedAt));
            assertTrue(tokenWindow.isActiveAt(verifiedAt, Duration.ofSeconds(30)));
            return expected;
        };

        TenantContext actual = verifier.verify("enterprise-primary", "opaque-bearer-token");

        assertSame(expected, actual);
        assertEquals("tenant-a", actual.tenantId());
        assertEquals("subject-a", actual.subjectId());
        assertEquals(
                Set.of(TenantPermissions.JOB_READ, TenantPermissions.VIEWER_READ),
                actual.permissions()
        );
        assertFalse(actual.permissions().contains("untrusted-token-permission"));
        assertFalse(actual.permissions().contains(TenantPermissions.JOB_DELETE));
    }

    @Test
    void roleMappingFailsClosedWhenVerifiedRolesGrantNoPermission() {
        TenantRoleMappingPolicy roleMapping = new TenantRoleMappingPolicy(
                "tenant-a",
                Map.of("document-reader", Set.of(TenantPermissions.JOB_READ))
        );

        assertTrue(roleMapping.resolve("subject-a", Set.of("unknown-role")).isEmpty());
    }
}
