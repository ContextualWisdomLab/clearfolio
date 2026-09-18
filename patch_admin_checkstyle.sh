cat << 'INNER_EOF' > /tmp/AdminController.patch
<<<<<<< SEARCH
    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessService tenant and permission guard
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
            @RequestHeader HttpHeaders headers) {
        var context = tenantAccessService.require(headers, TenantPermissions.ADMIN_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.belongsToTenant(context.tenantId())) {
                if (deadLettered == null || job.isDeadLettered() == deadLettered) {
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
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {
        var context = tenantAccessService.require(headers, TenantPermissions.ADMIN_WRITE);
        if (!conversionService.deleteJob(jobId, context)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
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
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {
        var context = tenantAccessService.require(headers, TenantPermissions.ADMIN_WRITE);
        var job = conversionService.getJob(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(context, job);
        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, "admin");
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }
=======
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
>>>>>>> REPLACE
INNER_EOF
python3 -c "import sys; content = open('src/main/java/com/clearfolio/viewer/controller/AdminController.java').read(); patch = open('/tmp/AdminController.patch').read(); search = patch.split('<<<<<<< SEARCH')[1].split('=======')[0].strip('\n'); replace = patch.split('=======')[1].split('>>>>>>> REPLACE')[0].strip('\n'); new_content = content.replace(search, replace); open('src/main/java/com/clearfolio/viewer/controller/AdminController.java', 'w').write(new_content);"
mvn -B verify
