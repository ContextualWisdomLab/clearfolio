with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "r") as f:
    content = f.read()

content = content.replace(
"""    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        controller = new AdminController(conversionService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }""",
"""    private TenantAccessService tenantAccessService;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void mockAuth(String permission) {
        when(tenantAccessService.require(any(org.springframework.http.HttpHeaders.class), eq(permission)))
                .thenReturn(new com.clearfolio.viewer.auth.TenantContext("tenant-1", "subject-1", Set.of(permission)));
    }

    private void mockAuthForbidden(String permission) {
        when(tenantAccessService.require(any(org.springframework.http.HttpHeaders.class), eq(permission)))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    }""")

content = content.replace(
"""import java.util.Arrays;""",
"""import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.Arrays;
import java.util.Set;""")

content = content.replace(
"""    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {""",
"""    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_READ);""")

content = content.replace(
"""    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {""",
"""    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_READ);""")

content = content.replace(
"""    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {""",
"""    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_READ);""")

content = content.replace(
"""    @Test
    void deleteJobReturnsNoContent() {""",
"""    @Test
    void getAllJobsReturnsForbiddenWhenNotAuthorized() {
        mockAuthForbidden(com.clearfolio.viewer.auth.TenantPermissions.JOB_READ);
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobReturnsNoContent() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_DELETE);""")

content = content.replace(
"""    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {""",
"""    @Test
    void deleteJobReturnsForbiddenWhenNotAuthorized() {
        mockAuthForbidden(com.clearfolio.viewer.auth.TenantPermissions.JOB_DELETE);
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_RETRY);""")

content = content.replace(
"""    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {""",
"""    @Test
    void retryDeadLetteredReturnsForbiddenWhenNotAuthorized() {
        mockAuthForbidden(com.clearfolio.viewer.auth.TenantPermissions.JOB_RETRY);
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID() + "/retry")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_RETRY);""")

content = content.replace(
"""    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {""",
"""    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        mockAuth(com.clearfolio.viewer.auth.TenantPermissions.JOB_RETRY);""")

with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "w") as f:
    f.write(content)
