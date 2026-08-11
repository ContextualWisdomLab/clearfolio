package com.clearfolio.viewer.auth;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable server-owned trust inputs for an identity verifier profile.
 *
 * <p>The policy deliberately keeps issuer, audience, and accepted signature
 * algorithms outside the untrusted bearer credential. Provider-specific
 * verifiers may use this value as configuration authority, but must not widen
 * it from token-controlled claims or headers.</p>
 *
 * @param profileId stable server-owned verifier profile identifier
 * @param issuer exact HTTPS issuer identifier accepted by the profile
 * @param audience exact audience required by the profile
 * @param allowedAlgorithms explicit non-empty signature-algorithm allowlist
 */
public record IssuerTrustPolicy(
        String profileId,
        String issuer,
        String audience,
        Set<String> allowedAlgorithms) {

    /**
     * Validates and defensively snapshots the configured trust authority.
     */
    public IssuerTrustPolicy {
        profileId = requireText(profileId, "identity trust profile id is required");
        issuer = requireHttpsIssuer(issuer);
        audience = requireText(audience, "identity trust audience is required");
        allowedAlgorithms = requireAlgorithms(allowedAlgorithms);
    }

    /**
     * Tests whether the configured policy explicitly permits an algorithm.
     *
     * @param algorithm exact case-sensitive signature algorithm identifier
     * @return {@code true} only when the algorithm is explicitly allowed
     */
    public boolean allowsAlgorithm(String algorithm) {
        return algorithm != null && allowedAlgorithms.contains(algorithm);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireHttpsIssuer(String issuer) {
        String requiredIssuer = requireText(issuer, "identity trust issuer is required");
        URI parsed;
        try {
            parsed = URI.create(requiredIssuer);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("identity trust issuer must be a valid HTTPS URI", ex);
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new IllegalArgumentException("identity trust issuer must use HTTPS");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new IllegalArgumentException("identity trust issuer must name a host");
        }
        if (parsed.getUserInfo() != null) {
            throw new IllegalArgumentException("identity trust issuer must not contain user information");
        }
        if (parsed.getQuery() != null) {
            throw new IllegalArgumentException("identity trust issuer must not contain a query");
        }
        if (parsed.getFragment() != null) {
            throw new IllegalArgumentException("identity trust issuer must not contain a fragment");
        }
        return requiredIssuer;
    }

    private static Set<String> requireAlgorithms(Set<String> algorithms) {
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("identity trust algorithms are required");
        }

        Set<String> copy = new LinkedHashSet<>();
        Set<String> caseFolded = new HashSet<>();
        for (String algorithm : algorithms) {
            if (algorithm == null || algorithm.isBlank()) {
                throw new IllegalArgumentException("identity trust algorithm must not be blank");
            }
            if ("none".equalsIgnoreCase(algorithm)) {
                throw new IllegalArgumentException("identity trust algorithm 'none' is forbidden");
            }
            if (!caseFolded.add(algorithm.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("identity trust algorithms must be case-distinct");
            }
            copy.add(algorithm);
        }
        return Set.copyOf(copy);
    }
}
