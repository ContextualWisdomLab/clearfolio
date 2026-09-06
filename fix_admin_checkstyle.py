with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

content = content.replace(
"""    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;""",
"""    /**
     * Conversion service for job operations.
     */
    private final DocumentConversionService conversionService;

    /**
     * Service to verify tenant access.
     */
    private final TenantAccessService tenantAccessService;""")

content = content.replace(
"""    public AdminController(
            final DocumentConversionService conversionService,
            final TenantAccessService tenantAccessSvc) {
        this.conversionService = conversionService;""",
"""    public AdminController(
            final DocumentConversionService conversionSvc,
            final TenantAccessService tenantAccessSvc) {
        this.conversionService = conversionSvc;""")

content = content.replace(
"""     * @param conversionService conversion service""",
"""     * @param conversionSvc conversion service""")

content = content.replace(
"""        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, "admin");
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }""",
"""        RetryDeadLetterResult result = conversionService
                .retryDeadLettered(jobId, "admin");
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "job is not eligible for retry");
        }""")

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
