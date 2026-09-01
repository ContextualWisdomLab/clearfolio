package com.clearfolio.viewer.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.security.RetryOperatorIdentityPort;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * HTTP adapter for tenant-authorized conversion administration.
 *
 * <p>The controller translates authenticated request context into application
 * service calls and HTTP responses. Resource ownership remains fail-closed:
 * callers may only observe or mutate jobs owned by their verified tenant. Audit
 * operator identifiers are delegated to a privacy-specific port so this adapter
 * never owns cryptographic key material or emits raw/weakly hashed principals.</p>
 */
@RestController
public class AdminController {

    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;
    private final RetryOperatorIdentityPort retryOperatorIdentity;

    /**
     * Creates the admin HTTP adapter with explicit authorization and audit ports.
     *
     * @param conversionService conversion application service that owns job
     *                          lookup and lifecycle commands
     * @param tenantAccessService verifies request claims, permissions, and tenant
     *                            ownership before protected behavior is exposed
     * @param retryOperatorIdentity converts authenticated subject identifiers to
     *                              privacy-safe audit correlation metadata
     */
    public AdminController(
            final DocumentConversionService conversionService,
            final TenantAccessService tenantAccessService,
            final RetryOperatorIdentityPort retryOperatorIdentity) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
        this.retryOperatorIdentity = retryOperatorIdentity;
    }

    /**
     * Retrieves conversion jobs visible to the authenticated tenant.
     *
     * <p>Authorization is evaluated before any job data is returned. The optional
     * dead-letter filter changes only presentation and never widens the tenant
     * boundary.</p>
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers containing tenant identity and permissions
     * @return tenant-owned conversion jobs matching the requested state
     * @throws ResponseStatusException when authentication or authorization fails
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) final Boolean deadLettered,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();
        List<ConversionJob> tenantJobs = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.belongsToTenant(context.tenantId())) {
                tenantJobs.add(job);
            }
        }

        if (deadLettered == null) {
            return AdminJobListResponse.from(tenantJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : tenantJobs) {
            if (job.isDeadLettered() == deadLettered) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes one tenant-owned conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers containing tenant identity and delete permission
     * @return no content when the authorized deletion completes
     * @throws ResponseStatusException when the caller is unauthorized or the job
     *         does not exist within the caller's tenant
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(headers, TenantPermissions.JOB_DELETE);
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(context, job);
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries one tenant-owned dead-lettered conversion job.
     *
     * <p>The authenticated subject is converted through the dedicated audit
     * identity port before it crosses into lifecycle state. This avoids storing
     * plaintext principals or dictionary-recoverable unkeyed hashes while
     * preserving versioned audit correlation when a dedicated key is configured.</p>
     *
     * @param jobId conversion job identifier
     * @param headers request headers containing tenant identity and retry permission
     * @return accepted when the retry transition is scheduled
     * @throws ResponseStatusException for unauthorized, missing, cross-tenant, or
     *         non-retryable jobs
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(headers, TenantPermissions.JOB_RETRY);
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(context, job);

        String operatorId = retryOperatorIdentity.pseudonymize(context.subjectId());
        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, operatorId);
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }
}
