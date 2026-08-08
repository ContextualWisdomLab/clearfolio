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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

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
     * @param tenantAccessService tenant access service
     */
    public AdminController(DocumentConversionService conversionService, TenantAccessService tenantAccessService) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers carrying tenant claims
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) Boolean deadLettered,
            @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();
        List<ConversionJob> filtered = new ArrayList<>();

        if (deadLettered == null) {
            for (ConversionJob job : allJobs) {
                if (job.belongsToTenant(tenantContext.tenantId())) {
                    filtered.add(job);
                }
            }
            return AdminJobListResponse.from(filtered);
        }

        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered && job.belongsToTenant(tenantContext.tenantId())) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_DELETE);
        if (!conversionService.deleteJob(jobId, tenantContext)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_RETRY);

        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(tenantContext, job);

        String operatorHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tenantContext.subjectId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            operatorHash = HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

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
