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
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessService service for tenant access validation
     */
    public AdminController(DocumentConversionService conversionService, TenantAccessService tenantAccessService) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param headers HTTP request headers containing tenant claims
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @org.springframework.web.bind.annotation.RequestHeader org.springframework.http.HttpHeaders headers,
            @RequestParam(required = false) Boolean deadLettered) {
        tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.ADMIN_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        if (deadLettered == null) {
            return AdminJobListResponse.from(allJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param headers HTTP request headers containing tenant claims
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @org.springframework.web.bind.annotation.RequestHeader org.springframework.http.HttpHeaders headers,
            @PathVariable UUID jobId) {
        tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE);
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param headers HTTP request headers containing tenant claims
     * @param jobId conversion job identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @org.springframework.web.bind.annotation.RequestHeader org.springframework.http.HttpHeaders headers,
            @PathVariable UUID jobId) {
        com.clearfolio.viewer.auth.TenantContext context = tenantAccessService.require(headers, com.clearfolio.viewer.auth.TenantPermissions.ADMIN_WRITE);
        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, context.subjectId());
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }
}
