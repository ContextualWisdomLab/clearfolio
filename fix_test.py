import re

with open("src/test/java/com/clearfolio/viewer/controller/ConversionControllerTest.java", "r") as f:
    content = f.read()

# Rather than regex, just replace .exchange() with headers in specific test names
def inject_to_test(test_name, content):
    match = re.search(r'void ' + test_name + r'\(\) \{[\s\S]*?(?=\n    @Test|\n\})', content)
    if match:
        body = match.group(0)
        body_new = body.replace('.exchange()', '.headers(h -> h.setAll(DEMO_HEADERS))\n                .exchange()')
        return content.replace(body, body_new)
    return content

content = inject_to_test('downloadArtifactReturnsNotFoundWhenJobNotFound', content)
content = inject_to_test('downloadArtifactReturnsConflictWhenJobNotSucceeded', content)
content = inject_to_test('downloadArtifactReturnsNotFoundWhenArtifactMissing', content)
content = inject_to_test('downloadArtifactReturnsPdfWithAttachmentDispositionAndChecksum', content)
content = inject_to_test('downloadArtifactNormalizesUnsafeFilenameForContentDisposition', content)
content = inject_to_test('downloadArtifactHandlesNullFilename', content)

new_tests = """
    @Test
    void downloadArtifactRequiresJobReadPermission() {
        UUID jobId = UUID.randomUUID();
        webClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .headers(h -> {
                    h.set("X-Clearfolio-Tenant-Id", "tenant-1");
                    h.set("X-Clearfolio-Subject-Id", "user-1");
                })
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void downloadArtifactRequiresHeaders() {
        UUID jobId = UUID.randomUUID();
        webClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void downloadArtifactReturnsNotFoundForCrossTenantAccess() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "other-tenant", "other-user", "test.pdf", "application/pdf", "hash", 100, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .headers(h -> h.setAll(DEMO_HEADERS))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND");
    }
}"""

content = re.sub(r'\}\s*$', new_tests, content)

with open("src/test/java/com/clearfolio/viewer/controller/ConversionControllerTest.java", "w") as f:
    f.write(content)
