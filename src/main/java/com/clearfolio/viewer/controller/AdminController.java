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
import com.clearfolio.viewer.security.AuditPseudonymizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;

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
     * Conversion service for administrative actions.
     */
    private final DocumentConversionService conversionService;

    /**
     * Pseudonymizer for operator tracking.
     */
    private final AuditPseudonymizer auditPseudonymizer;

    /**
     * Creates a controller for admin operations.
     *
     * @param docConversionService conversion service
     * @param auditSecret secret for audit
     * @param auditKeyVersion key version for audit
     */
    public AdminController(
            final DocumentConversionService docConversionService,
            @Value("${conversion.audit-pseudonym-secret:}")
            final String auditSecret,
            @Value("${conversion.audit-pseudonym-key-version:}")
            final String auditKeyVersion) {
        this.conversionService = docConversionService;
        this.auditPseudonymizer = new AuditPseudonymizer(
                auditSecret,
                auditKeyVersion,
                "clearfolio:admin-operator:v1"
        );
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) final Boolean deadLettered
    ) {
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
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable final UUID jobId) {
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable final UUID jobId,
            @RequestHeader(
                    value = "X-Operator-Id",
                    required = false
            ) final String operatorId) {
        String pseudonym = auditPseudonymizer.fingerprint(operatorId);
        RetryDeadLetterResult result = conversionService.retryDeadLettered(
                jobId, pseudonym);
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
