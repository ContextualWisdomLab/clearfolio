package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IssuerTrustPolicyTest {

    @Test
    void preservesExplicitServerOwnedTrustInputs() {
        Set<String> algorithms = new LinkedHashSet<>(Set.of("RS256", "ES256"));

        IssuerTrustPolicy policy = new IssuerTrustPolicy(
                "corp-oidc-v1",
                "https://id.example.com/tenant-a",
                "clearfolio-api",
                algorithms
        );
        algorithms.add("PS256");

        assertEquals("corp-oidc-v1", policy.profileId());
        assertEquals("https://id.example.com/tenant-a", policy.issuer());
        assertEquals("clearfolio-api", policy.audience());
        assertEquals(Set.of("RS256", "ES256"), policy.allowedAlgorithms());
        assertTrue(policy.allowsAlgorithm("RS256"));
        assertFalse(policy.allowsAlgorithm("PS256"));
        assertFalse(policy.allowsAlgorithm(null));
        assertThrows(UnsupportedOperationException.class, () -> policy.allowedAlgorithms().add("PS256"));
    }

    @Test
    void rejectsMissingOrAmbiguousTrustAuthority() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy(null, "https://id.example.com", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy(" ", "https://id.example.com", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", null, "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", " ", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "http://id.example.com", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://user@id.example.com", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com?tenant=a", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com#keys", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://", "clearfolio-api", Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", null, Set.of("RS256"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", " ", Set.of("RS256"))
        );
    }

    @Test
    void rejectsImplicitOrUnsafeAlgorithmPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", "clearfolio-api", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", "clearfolio-api", Set.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", "clearfolio-api", Set.of("none"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", "clearfolio-api", Set.of(" "))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IssuerTrustPolicy("corp", "https://id.example.com", "clearfolio-api", Set.of("RS256", "rs256"))
        );
    }
}
