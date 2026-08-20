package com.clearfolio.viewer.auth;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable server-owned mapping from external identity roles to one Clearfolio
 * tenant's internal permissions.
 *
 * <p>External credentials may present role names, but those role strings never
 * become Clearfolio permissions directly. Only permissions configured in this
 * policy are emitted into a {@link TenantContext}. Binding the policy to one
 * tenant also prevents an untrusted token claim from selecting arbitrary tenant
 * authority.</p>
 *
 * @param tenantId fixed Clearfolio tenant controlled by server configuration
 * @param rolePermissions immutable role-to-permission mapping controlled by the server
 */
public record TenantRoleMappingPolicy(
        String tenantId,
        Map<String, Set<String>> rolePermissions) {

    /**
     * Validates and defensively snapshots server-owned mapping authority.
     */
    public TenantRoleMappingPolicy {
        tenantId = requireCanonicalText(tenantId, "tenant role mapping tenant is required");
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            throw new IllegalArgumentException("tenant role mapping entries are required");
        }

        Map<String, Set<String>> immutableMappings = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : rolePermissions.entrySet()) {
            String role = requireCanonicalText(entry.getKey(), "tenant role name is required");
            Set<String> configuredPermissions = entry.getValue();
            if (configuredPermissions == null || configuredPermissions.isEmpty()) {
                throw new IllegalArgumentException("tenant role permissions are required");
            }

            Set<String> immutablePermissions = new LinkedHashSet<>();
            for (String permission : configuredPermissions) {
                immutablePermissions.add(requireCanonicalText(
                        permission,
                        "tenant role permission is required"
                ));
            }
            immutableMappings.put(role, Set.copyOf(immutablePermissions));
        }
        rolePermissions = Map.copyOf(immutableMappings);
    }

    /**
     * Resolves verified external role claims into a Clearfolio tenant context.
     *
     * <p>Unknown roles are ignored. Missing or malformed subject authority and a
     * role set that grants no configured Clearfolio permission fail closed with
     * an empty result.</p>
     *
     * @param subjectId stable verified external subject identifier
     * @param externalRoles verified external role claims
     * @return mapped tenant context when at least one configured permission applies
     */
    public Optional<TenantContext> resolve(String subjectId, Set<String> externalRoles) {
        if (!isCanonicalText(subjectId) || externalRoles == null || externalRoles.isEmpty()) {
            return Optional.empty();
        }

        Set<String> mappedPermissions = new LinkedHashSet<>();
        for (String externalRole : externalRoles) {
            Set<String> configuredPermissions = rolePermissions.get(externalRole);
            if (configuredPermissions != null) {
                mappedPermissions.addAll(configuredPermissions);
            }
        }
        if (mappedPermissions.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new TenantContext(tenantId, subjectId, mappedPermissions));
    }

    private static String requireCanonicalText(String value, String message) {
        if (!isCanonicalText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean isCanonicalText(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.strip())
                && value.indexOf('\u0000') < 0;
    }
}
