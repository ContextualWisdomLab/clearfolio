const fs = require('fs');

const path = 'src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java';
let content = fs.readFileSync(path, 'utf8');

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

if (!content.includes('getAllJobsFailsWithoutReadPermission')) {
    content = content.replace('}', negativeTests + '\n}');
    fs.writeFileSync(path, content);
}
