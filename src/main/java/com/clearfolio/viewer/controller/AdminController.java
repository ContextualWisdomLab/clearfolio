package com.clearfolio.viewer.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.security.AuditPseudonymizer;
import com.clearfolio.viewer.config.ConversionProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    /**
     * Conversion service for jobs.
     */
    private final DocumentConversionService conversionService;

    /**
     * Tenant access service for isolation.
     */
    private final TenantAccessService tenantAccessService;

    /**
     * Audit pseudonymizer for operator ID.
     */
    private final AuditPseudonymizer auditPseudonymizer;

    /**
     * Creates a controller for admin operations.
     *
     * @param svc conversion service
     * @param accessSvc tenant access service
     * @param properties conversion properties
     */
    public AdminController(
            final DocumentConversionService svc,
            final TenantAccessService accessSvc,
            final ConversionProperties properties) {
        this.conversionService = svc;
        this.tenantAccessService = accessSvc;
        this.auditPseudonymizer = new AuditPseudonymizer(
                properties.getAuditPseudonymSecret(),
                properties.getAuditPseudonymKeyVersion());
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers HTTP headers
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) final Boolean deadLettered,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        if (deadLettered == null) {
            List<ConversionJob> tenantAllJobs = new ArrayList<>();
            for (ConversionJob job : allJobs) {
                if (job.belongsToTenant(context.tenantId())) {
                    tenantAllJobs.add(job);
                }
            }
            return AdminJobListResponse.from(tenantAllJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered
                    && job.belongsToTenant(context.tenantId())) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers HTTP headers
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_DELETE);
        Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        tenantAccessService.requireSameTenant(context, job.get());
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers HTTP headers
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_RETRY);
        Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        tenantAccessService.requireSameTenant(context, job.get());
        String operatorId = auditPseudonymizer.fingerprint(context.subjectId());
        RetryDeadLetterResult result = conversionService
                .retryDeadLettered(jobId, operatorId);
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
