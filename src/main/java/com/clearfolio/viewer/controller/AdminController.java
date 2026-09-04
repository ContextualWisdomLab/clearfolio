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
 * <p>The controller authenticates the request, delegates tenant-scoped
 * aggregate access to the conversion application service, and translates
 * domain outcomes into HTTP responses. It never fetches a global job
 * collection or raw cross-tenant aggregate to reconstruct ownership locally.
 * Audit operator identifiers are delegated to a privacy-specific port so this
 * adapter owns neither cryptographic key material nor persistent identity
 * semantics.</p>
 */
@RestController
public class AdminController {

    /**
     * Application service for document conversion.
     */
    private final DocumentConversionService conversionService;

    /**
     * Validates tenant claims.
     */
    private final TenantAccessService tenantAccessService;

    /**
     * Pseudonymizes audit identity.
     */
    private final RetryOperatorIdentityPort retryOperatorIdentity;

    /**
     * Creates the admin HTTP adapter with explicit application, authorization,
     * and audit-identity ports.
     *
     * @param pConversionService conversion application service
     * @param pTenantAccessService verifies request claims
     * @param pRetryOperatorIdentity pseudonymizes audit identity
     */
    public AdminController(
            final DocumentConversionService pConversionService,
            final TenantAccessService pTenantAccessService,
            final RetryOperatorIdentityPort pRetryOperatorIdentity) {
        this.conversionService = pConversionService;
        this.tenantAccessService = pTenantAccessService;
        this.retryOperatorIdentity = pRetryOperatorIdentity;
    }

    /**
     * Retrieves conversion jobs visible to the authenticated tenant.
     *
     * <p>Authorization runs before the tenant-scoped application query. The
     * optional dead-letter filter is presentation behavior over an already
     * tenant-bounded result and cannot widen the ownership boundary.</p>
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers HTTP headers with tenant claims
     * @return tenant-owned conversion jobs matching the requested state
     * @throws ResponseStatusException if auth fails
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) final Boolean deadLettered,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> tenantJobs = conversionService
                .getJobsForTenant(context);

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
     * <p>Missing and cross-tenant identifiers are intentionally mapped to the
     * same not-found response by the application service, preventing a
     * resource existence oracle.</p>
     *
     * @param jobId conversion job identifier
     * @param headers HTTP headers with tenant claims
     * @return no content when the authorized deletion completes
     * @throws ResponseStatusException if auth fails
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_DELETE);
        if (!conversionService.deleteJob(
                jobId, context)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries one tenant-owned dead-lettered conversion job.
     *
     * <p>The authenticated subject is converted through the dedicated audit
     * identity port before the tenant-scoped lifecycle command runs. The
     * application service collapses missing and cross-tenant resources to the
     * same NOT_FOUND outcome, while the audit identifier remains correlation
     * metadata rather than an authorization input.</p>
     *
     * @param jobId conversion job identifier
     * @param headers HTTP headers with tenant claims
     * @return accepted when the retry transition is scheduled
     * @throws ResponseStatusException if auth fails
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_RETRY);
        String operatorId = retryOperatorIdentity.pseudonymize(
                context.subjectId());
        RetryDeadLetterResult result = conversionService.retryDeadLettered(
                jobId, operatorId, context);
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }
}
