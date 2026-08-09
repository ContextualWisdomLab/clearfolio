package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpHeaders;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;

    private static final String TEST_TENANT = "test-tenant";
    private static final String OTHER_TENANT = "other-tenant";
    private static final String TEST_SUBJECT = "test-operator";

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void mockContext(String permission, String tenantId) {
        TenantContext context = new TenantContext(tenantId, TEST_SUBJECT, Set.of(permission));
        when(tenantAccessService.require(any(), eq(permission))).thenReturn(context);
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        mockContext(TenantPermissions.JOB_READ, TEST_TENANT);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        mockContext(TenantPermissions.JOB_READ, TEST_TENANT);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markFailed("err");
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        mockContext(TenantPermissions.JOB_READ, TEST_TENANT);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        job1.markFailed("err");
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "b.pdf", "application/pdf", "hash-b", 100L, 3);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByTenantId() {
        mockContext(TenantPermissions.JOB_READ, TEST_TENANT);
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), OTHER_TENANT, "sub", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        mockContext(TenantPermissions.JOB_DELETE, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(jobId);
    }

    @Test
    void deleteJobReturnsNotFoundWhenNotOwned() {
        mockContext(TenantPermissions.JOB_DELETE, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, OTHER_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() throws Exception {
        mockContext(TenantPermissions.JOB_RETRY, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(TEST_SUBJECT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expectedHash = java.util.HexFormat.of().formatHex(hash);

        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() throws Exception {
        mockContext(TenantPermissions.JOB_RETRY, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(TEST_SUBJECT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expectedHash = java.util.HexFormat.of().formatHex(hash);

        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() throws Exception {
        mockContext(TenantPermissions.JOB_RETRY, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, TEST_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(TEST_SUBJECT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expectedHash = java.util.HexFormat.of().formatHex(hash);

        when(conversionService.retryDeadLettered(jobId, expectedHash)).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotOwned() {
        mockContext(TenantPermissions.JOB_RETRY, TEST_TENANT);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, OTHER_TENANT, "sub", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, TEST_TENANT)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(any(), any());
    }
}
