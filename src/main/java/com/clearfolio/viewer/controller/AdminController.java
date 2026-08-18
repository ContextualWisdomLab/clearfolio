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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import java.security.GeneralSecurityException;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    /** Conversion service. */
    private final DocumentConversionService conversionService;
    /** Tenant access service. */
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param cvcService conversion service
     * @param taService tenant access service
     */
    public AdminController(
            final DocumentConversionService cvcService,
            final TenantAccessService taService) {
        this.conversionService = cvcService;
        this.tenantAccessService = taService;
    }

        /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param headers request headers
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestHeader final HttpHeaders headers,
            @RequestParam(required = false) final Boolean deadLettered) {
        TenantContext context = tenantAccessService.require(
                headers, "admin:read");
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (!job.belongsToTenant(context.tenantId())) {
                continue;
            }
            if (deadLettered == null || job.isDeadLettered() == deadLettered) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

        /**
     * Deletes a conversion job.
     *
     * @param headers request headers
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        TenantContext context = tenantAccessService.require(
                headers, "admin:write");
        ConversionJob job = null;
        for (ConversionJob j : conversionService.getAllJobs()) {
            if (j.getJobId().equals(jobId)) {
                job = j;
                break;
            }
        }
        tenantAccessService.requireSameTenant(context, job);
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param headers request headers
     * @param jobId conversion job identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        TenantContext context = tenantAccessService.require(
                headers, "admin:write");

        ConversionJob job = null;
        for (ConversionJob j : conversionService.getAllJobs()) {
            if (j.getJobId().equals(jobId)) {
                job = j;
                break;
            }
        }
        tenantAccessService.requireSameTenant(context, job);

        String actor;
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = context.subjectId().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            actor = "admin-" + java.util.HexFormat.of().formatHex(
                    md.digest(bytes));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }

        RetryDeadLetterResult result = conversionService
                .retryDeadLettered(jobId, actor);
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
