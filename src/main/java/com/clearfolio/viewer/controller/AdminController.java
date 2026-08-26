package com.clearfolio.viewer.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessService tenant access service
     */
    public AdminController(
            final DocumentConversionService conversionService,
            final TenantAccessService tenantAccessService) {
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
            @RequestParam(required = false) final Boolean deadLettered,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_READ);
        final Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        final List<ConversionJob> filtered = new ArrayList<>();
        for (final ConversionJob job : allJobs) {
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
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_DELETE);

        final Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty() || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }

        conversionService.deleteJob(jobId);
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
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader final HttpHeaders headers) {
        final TenantContext context = tenantAccessService.require(
                headers, TenantPermissions.JOB_RETRY);

        final Optional<ConversionJob> job = conversionService.getJob(jobId);
        if (job.isEmpty() || !job.get().belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }

        final String operatorId = pseudonymize(context.subjectId());
        final RetryDeadLetterResult result = conversionService
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

    /**
     * Pseudonymizes a subject ID by hashing it with SHA-256.
     *
     * @param subjectId raw subject identifier
     * @return hex-encoded SHA-256 hash
     */
    private static String pseudonymize(final String subjectId) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(
                    subjectId.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
