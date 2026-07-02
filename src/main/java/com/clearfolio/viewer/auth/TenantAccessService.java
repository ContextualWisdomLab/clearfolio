package com.clearfolio.viewer.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Enforces request tenant claims and endpoint permissions.
 */
@Service
public class TenantAccessService {

    /**
     * Resolves tenant claims and verifies the required permission.
     *
     * @param headers request headers
     * @param permission required permission
     * @return verified tenant context
     */
    public TenantContext require(HttpHeaders headers, String permission) {
        TenantContext context = TenantContext.fromHeaders(headers)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "auth token required"
                ));

        if (!context.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "missing permission: " + permission);
        }

        return context;
    }

    /**
     * Hides resources that do not belong to the request tenant.
     *
     * @param context verified tenant context
     * @param job conversion job being accessed
     */
    public void requireSameTenant(TenantContext context, ConversionJob job) {
        if (context == null || job == null || !job.belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
    }
}
