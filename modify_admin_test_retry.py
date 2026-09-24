import sys

with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "r") as f:
    content = f.read()

search_accepted = """    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isAccepted();
    }"""

replace_accepted = """    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isAccepted();
    }"""

search_notfound = """    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }"""

replace_notfound = """    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }"""

search_conflict = """    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }"""

replace_conflict = """    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }"""

content = content.replace(search_accepted, replace_accepted)
content = content.replace(search_notfound, replace_notfound)
content = content.replace(search_conflict, replace_conflict)

with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "w") as f:
    f.write(content)
