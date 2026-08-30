package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String SUBJECT_ID = "admin-subject";
    private static final TenantContext READ_CONTEXT = new TenantContext(
            TENANT_ID,
            SUBJECT_ID,
            Set.of(TenantPermissions.ADMIN_READ)
    );
    private static final TenantContext WRITE_CONTEXT = new TenantContext(
            TENANT_ID,
            SUBJECT_ID,
            Set.of(TenantPermissions.ADMIN_WRITE)
    );

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(conversionService, new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsTenantScopedJobsWhenNoFilterProvided() {
        ConversionJob job1 = ownedJob("a.pdf", "hash-a");
        ConversionJob job2 = ownedJob("b.pdf", "hash-b");
        when(conversionService.getAllJobs(READ_CONTEXT)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = ownedJob("a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = ownedJob("b.pdf", "hash-b");
        when(conversionService.getAllJobs(READ_CONTEXT)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = ownedJob("a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = ownedJob("b.pdf", "hash-b");
        when(conversionService.getAllJobs(READ_CONTEXT)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_READ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void getAllJobsRequiresAdminReadPermission() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deleteJobReturnsNoContentForOwnedJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, WRITE_CONTEXT)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, WRITE_CONTEXT)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, WRITE_CONTEXT)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenMissingOrForeign() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, WRITE_CONTEXT)).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, WRITE_CONTEXT)).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addAdminHeaders(headers, TenantPermissions.ADMIN_WRITE))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static ConversionJob ownedJob(String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                TENANT_ID,
                "submitter",
                fileName,
                "application/pdf",
                hash,
                100L,
                3
        );
    }

    private static void addAdminHeaders(HttpHeaders headers, String permission) {
        headers.add(TenantContext.TENANT_ID_HEADER, TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
