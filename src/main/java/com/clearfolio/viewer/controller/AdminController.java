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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    /** Hex formatter for pseudonyms. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Service to perform document conversion jobs. */
    private final DocumentConversionService conversionService;

    /** Service to authorize tenants and track permissions. */
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionServiceToUse conversion service
     * @param tenantAccessServiceToUse tenant access service
     */
    public AdminController(
            final DocumentConversionService conversionServiceToUse,
            final TenantAccessService tenantAccessServiceToUse) {
        this.conversionService = conversionServiceToUse;
        this.tenantAccessService = tenantAccessServiceToUse;
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param headers request headers carrying tenant claims
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestHeader final HttpHeaders headers,
            @RequestParam(required = false) final Boolean deadLettered
    ) {
        TenantContext context = tenantAccessService
                .require(headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.belongsToTenant(context.tenantId())) {
                if (deadLettered == null
                        || job.isDeadLettered() == deadLettered) {
                    filtered.add(job);
                }
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param headers request headers carrying tenant claims
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        TenantContext context = tenantAccessService
                .require(headers, TenantPermissions.JOB_DELETE);
        Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty()
                || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "job not found");
        }

        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param headers request headers carrying tenant claims
     * @param jobId conversion job identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        TenantContext context = tenantAccessService
                .require(headers, TenantPermissions.JOB_RETRY);
        Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty()
                || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "job not found");
        }

        String pseudonymizedOperatorId = hashSubjectId(context.subjectId());
        RetryDeadLetterResult result = conversionService
                .retryDeadLettered(jobId, pseudonymizedOperatorId);
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }

    private String hashSubjectId(final String subjectId) {
        if (subjectId == null) {
            return "absent";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    subjectId.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
