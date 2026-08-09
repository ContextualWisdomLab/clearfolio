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
import org.springframework.web.bind.annotation.RequestHeader;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();

    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessService tenant access service for authorization
     */
    public AdminController(DocumentConversionService conversionService, TenantAccessService tenantAccessService) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
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
            @RequestParam(required = false) Boolean deadLettered,
            @RequestHeader org.springframework.http.HttpHeaders headers) {

        com.clearfolio.viewer.auth.TenantContext context = tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.JOB_READ);

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
     * @param jobId conversion job identifier
     * @param headers request headers
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable UUID jobId,
            @RequestHeader org.springframework.http.HttpHeaders headers) {

        com.clearfolio.viewer.auth.TenantContext context = tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.JOB_DELETE);
        java.util.Optional<ConversionJob> job = conversionService.getJob(jobId);

        if (job.isEmpty() || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }

        conversionService.deleteJob(jobId);
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
            @PathVariable UUID jobId,
            @RequestHeader org.springframework.http.HttpHeaders headers) {

        com.clearfolio.viewer.auth.TenantContext context = tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.JOB_RETRY);
        java.util.Optional<ConversionJob> job = conversionService.getJob(jobId);

        if (job.isEmpty() || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }

        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] hash = digest.digest(context.subjectId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String operatorHash = HEX_FORMAT.formatHex(hash);

        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, operatorHash);
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }
}
