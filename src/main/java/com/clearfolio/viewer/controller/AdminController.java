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
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    /** Conversion service. */
    private final DocumentConversionService conversionService;

    /** Tenant access service. */
    private final TenantAccessService tenantAccessService;

    /** Hexadecimal formatter. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static String pseudonymize(final String subjectId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    subjectId.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * Creates a controller for admin operations.
     *
     * @param svc conversion service
     * @param auth tenant access service
     */
    public AdminController(
            final DocumentConversionService svc,
            final TenantAccessService auth) {
        this.conversionService = svc;
        this.tenantAccessService = auth;
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
        TenantContext ctx = tenantAccessService.require(
                headers, TenantPermissions.JOB_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        if (deadLettered == null) {
            List<ConversionJob> tenantJobs = new ArrayList<>();
            for (ConversionJob job : allJobs) {
                if (job.belongsToTenant(ctx.tenantId())) {
                    tenantJobs.add(job);
                }
            }
            return AdminJobListResponse.from(tenantJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered
                    && job.belongsToTenant(ctx.tenantId())) {
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
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        TenantContext ctx = tenantAccessService.require(
                headers, TenantPermissions.JOB_DELETE);
        if (!conversionService.deleteJob(jobId, ctx)) {
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
        TenantContext ctx = tenantAccessService.require(
                headers, TenantPermissions.JOB_RETRY);
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(ctx, job);
        String operatorId = pseudonymize(ctx.subjectId());
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
