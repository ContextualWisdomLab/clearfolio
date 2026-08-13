package com.clearfolio.viewer.auth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;

/**
 * Header-derived tenant and permission claims for one authenticated request.
 *
 * <p>The tenant identifier defines the data-isolation boundary, the subject
 * identifies the authenticated user or service, and the immutable permission
 * set limits which operations that subject may perform. Callers should obtain
 * contexts through the signed-claim authorization boundary rather than trusting
 * raw client headers directly.</p>
 *
 * @param tenantId normalized tenant identifier that scopes all repository and
 *        artifact access
 * @param subjectId normalized authenticated user or service identifier
 * @param permissions immutable normalized permission claims for the subject
 */
public record TenantContext(String tenantId, String subjectId, Set<String> permissions) {

    /**
     * Header carrying the tenant isolation boundary.
     */
    public static final String TENANT_ID_HEADER = "X-Clearfolio-Tenant-Id";

    /**
     * Header carrying the authenticated user or service subject.
     */
    public static final String SUBJECT_ID_HEADER = "X-Clearfolio-Subject-Id";

    /**
     * Header carrying comma-separated permission claims.
     */
    public static final String PERMISSIONS_HEADER = "X-Clearfolio-Permissions";

    /**
     * Header carrying the epoch-second issue time for signed gateway claims.
     */
    public static final String CLAIMS_ISSUED_AT_HEADER = "X-Clearfolio-Claims-Issued-At";

    /**
     * Header carrying the HMAC signature for signed gateway claims.
     */
    public static final String CLAIMS_SIGNATURE_HEADER = "X-Clearfolio-Claims-Signature";

    /**
     * Demo tenant used by the built-in buyer-demo shell.
     */
    public static final String DEMO_TENANT_ID = "buyer-demo";

    /**
     * Demo subject used by the built-in buyer-demo shell.
     */
    public static final String DEMO_SUBJECT_ID = "buyer-demo-operator";

    /**
     * Creates a tenant context and normalizes claim values.
     *
     * @param tenantId tenant claim
     * @param subjectId subject claim
     * @param permissions permission claims
     */
    public TenantContext {
        tenantId = sanitize(tenantId);
        subjectId = sanitize(subjectId);
        permissions = permissions == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /**
     * Builds a tenant context from request headers.
     *
     * @param headers request headers
     * @return tenant context when required claims are present
     */
    public static Optional<TenantContext> fromHeaders(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }

        String tenantId = sanitize(headers.getFirst(TENANT_ID_HEADER));
        String subjectId = sanitize(headers.getFirst(SUBJECT_ID_HEADER));
        Set<String> permissions = permissionsOf(headers.getFirst(PERMISSIONS_HEADER));
        if (tenantId == null || subjectId == null || permissions.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new TenantContext(tenantId, subjectId, permissions));
    }

    /**
     * Returns whether the context contains the supplied permission.
     *
     * @param permission required permission
     * @return true when permission is present
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Returns the canonical comma-separated permission claim string.
     *
     * @return canonical permission string
     */
    public String canonicalPermissions() {
        return String.join(",", permissions);
    }

    private static Set<String> permissionsOf(final String raw) {
        final String normalized = sanitize(raw);
        if (normalized == null) {
            return Set.of();
        }

        final LinkedHashSet<String> parsed = new LinkedHashSet<>();
        // Performance optimization: Avoid String.split() and Streams
        // for simple delimited tokens to reduce GC pressure.
        int start = 0;
        int nextComma;
        while ((nextComma = normalized.indexOf(',', start)) != -1) {
            final String token = sanitize(
                    normalized.substring(start, nextComma));
            if (token != null) {
                parsed.add(token);
            }
            start = nextComma + 1;
        }
        final String lastToken = sanitize(normalized.substring(start));
        if (lastToken != null) {
            parsed.add(lastToken);
        }
        return parsed;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value
                .replace("\u0000", "")
                .strip();
        return sanitized.isEmpty() ? null : sanitized;
    }
}
