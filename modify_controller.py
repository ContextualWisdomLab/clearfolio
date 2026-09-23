import sys

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

search1 = """    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")"""

replace1 = """    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers carrying tenant claims
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")"""

search2 = """    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")"""

replace2 = """    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")"""

search3 = """    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")"""

replace3 = """    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")"""

content = content.replace(search1, replace1)
content = content.replace(search2, replace2)
content = content.replace(search3, replace3)

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
