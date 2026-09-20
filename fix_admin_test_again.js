const fs = require('fs');

const path = 'src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java';
let content = fs.readFileSync(path, 'utf8');

// I will re-apply the auth headers properly to the existing valid class
content = content.replace(
    'import com.clearfolio.viewer.service.RetryDeadLetterResult;',
    'import com.clearfolio.viewer.auth.TenantAccessService;\nimport com.clearfolio.viewer.auth.TenantContext;\nimport com.clearfolio.viewer.auth.TenantPermissions;\nimport com.clearfolio.viewer.service.RetryDeadLetterResult;'
);

content = content.replace(
    '    private DocumentConversionService conversionService;\n    private WebTestClient webTestClient;\n    private AdminController controller;\n\n    @BeforeEach\n    void setUp() {\n        conversionService = mock(DocumentConversionService.class);\n        controller = new AdminController(conversionService);',
    '    private DocumentConversionService conversionService;\n    private TenantAccessService tenantAccessService;\n    private WebTestClient webTestClient;\n    private AdminController controller;\n\n    @BeforeEach\n    void setUp() {\n        conversionService = mock(DocumentConversionService.class);\n        tenantAccessService = new TenantAccessService();\n        controller = new AdminController(conversionService, tenantAccessService);'
);

content = content.replace(/\.uri\("\/api\/v1\/admin\/convert\/jobs([^"]*)"\)/g, '.uri("/api/v1/admin/convert/jobs$1")\n                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")\n                .header("X-Clearfolio-Subject-Id", "admin")\n                .header("X-Clearfolio-Permissions", "admin:read")');

content = content.replace(/\.uri\("\/api\/v1\/admin\/convert\/jobs\/" \+ jobId\)/g, '.uri("/api/v1/admin/convert/jobs/" + jobId)\n                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")\n                .header("X-Clearfolio-Subject-Id", "admin")\n                .header("X-Clearfolio-Permissions", "admin:write")');

content = content.replace(/\.uri\("\/api\/v1\/admin\/convert\/jobs\/" \+ jobId \+ "\/retry"\)/g, '.uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")\n                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")\n                .header("X-Clearfolio-Subject-Id", "admin")\n                .header("X-Clearfolio-Permissions", "admin:write")');

const negativeTests = `
    @Test
    void getAllJobsFailsWithoutReadPermission() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")
                .header("X-Clearfolio-Subject-Id", "admin")
                .header("X-Clearfolio-Permissions", "job:read")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteJobFailsWithoutWritePermission() {
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID())
                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")
                .header("X-Clearfolio-Subject-Id", "admin")
                .header("X-Clearfolio-Permissions", "admin:read")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void retryDeadLetteredFailsWithoutWritePermission() {
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + UUID.randomUUID() + "/retry")
                .header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")
                .header("X-Clearfolio-Subject-Id", "admin")
                .header("X-Clearfolio-Permissions", "admin:read")
                .exchange()
                .expectStatus().isForbidden();
    }
`;

content = content.replace('}\n', negativeTests + '\n}\n');

fs.writeFileSync(path, content);
