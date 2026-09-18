cat << 'INNER_EOF' > /tmp/AdminControllerTest.patch
<<<<<<< SEARCH
    @Test
    void endpointsRequireAuthorization() {
=======
    @Test
    void deleteJobReturnsNotFoundWhenNotExists() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.ADMIN_WRITE)));
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(new TenantContext("tenant-1", "subject-1", Set.of(TenantPermissions.ADMIN_WRITE)));
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void endpointsRequireAuthorization() {
>>>>>>> REPLACE
INNER_EOF
