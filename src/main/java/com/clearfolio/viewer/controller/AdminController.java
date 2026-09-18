package com.clearfolio.viewer.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    /**
     * The document conversion service.
     */
    private final DocumentConversionService conversionSvc;

    /**
     * The tenant access service.
     */
    private final TenantAccessService tenantAccessSvc;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionSvc conversion service
     * @param tenantAccessSvc tenant and permission guard
     */
    public AdminController(
            final DocumentConversionService conversionSvc,
            final TenantAccessService tenantAccessSvc) {
        this.conversionSvc = conversionSvc;
        this.tenantAccessSvc = tenantAccessSvc;
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) final Boolean deadLettered,
            @RequestHeader final HttpHeaders headers) {
        var context = tenantAccessSvc.require(
                headers, TenantPermissions.ADMIN_READ);
        Iterable<ConversionJob> allJobs = conversionSvc.getAllJobs();

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.belongsToTenant(context.tenantId())) {
                boolean match = deadLettered == null
                        || job.isDeadLettered() == deadLettered;
                if (match) {
                    filtered.add(job);
                }
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        var context = tenantAccessSvc.require(
                headers, TenantPermissions.ADMIN_WRITE);
        if (!conversionSvc.deleteJob(jobId, context)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        var context = tenantAccessSvc.require(
                headers, TenantPermissions.ADMIN_WRITE);
        var job = conversionSvc.getJob(jobId).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessSvc.requireSameTenant(context, job);
        RetryDeadLetterResult result =
                conversionSvc.retryDeadLettered(jobId, "admin");
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
