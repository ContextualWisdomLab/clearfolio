package com.clearfolio.viewer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for admin-specific endpoints.
 */
@RestController
public class AdminController {

    private final DocumentConversionService conversionService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     */
    public AdminController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(@RequestParam(required = false) Boolean deadLettered) {
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
}
