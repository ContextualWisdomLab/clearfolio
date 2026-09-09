with open('src/main/java/com/clearfolio/viewer/controller/AdminController.java', 'r') as f:
    content = f.read()

content = content.replace(
"""     * @param conversionService conversion service
     */""",
"""     * @param conversionService conversion service
     * @param tenantAccessService tenant access service
     */""")

content = content.replace(
"""     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */""",
"""     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers
     * @return list of conversion jobs
     */""")

content = content.replace(
"""     * @param jobId conversion job identifier
     * @return no content on success
     */""",
"""     * @param jobId conversion job identifier
     * @param headers request headers
     * @return no content on success
     */""")

content = content.replace(
"""     * @param jobId conversion job identifier
     * @return accepted response on success
     */""",
"""     * @param jobId conversion job identifier
     * @param headers request headers
     * @return accepted response on success
     */""")

content = content.replace(
"""    public AdminJobListResponse getAllJobs(@RequestParam(required = false) Boolean deadLettered, @RequestHeader HttpHeaders headers) {""",
"""    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) Boolean deadLettered,
            @RequestHeader HttpHeaders headers) {""")

content = content.replace(
"""    public ResponseEntity<Void> deleteJob(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {""",
"""    public ResponseEntity<Void> deleteJob(
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {""")

content = content.replace(
"""    public ResponseEntity<Void> retryDeadLettered(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {""",
"""    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {""")

content = content.replace(
"""    public AdminController(DocumentConversionService conversionService, TenantAccessService tenantAccessService) {""",
"""    public AdminController(
            DocumentConversionService conversionService,
            TenantAccessService tenantAccessService) {""")

with open('src/main/java/com/clearfolio/viewer/controller/AdminController.java', 'w') as f:
    f.write(content)
