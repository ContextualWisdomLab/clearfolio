package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Verifies that admin operations keep the tenant boundary after permission checks.
 */
class AdminTenantIsolationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String SUBJECT_A = "operator-a";

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(
                conversionService,
                new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listDoesNotExposeJobsOwnedByAnotherTenant() {
        ConversionJob ownJob = job(TENANT_A, "own.pdf");
        ConversionJob foreignJob = job(TENANT_B, "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(List.of(ownJob, foreignJob));

        authorizedGet(TenantPermissions.JOB_READ)
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("own.pdf");
    }

    @Test
    void deleteHidesJobOwnedByAnotherTenant() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(TENANT_B, "foreign.pdf")));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, TENANT_A)
                .header(TenantContext.SUBJECT_ID_HEADER, SUBJECT_A)
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_DELETE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryHidesJobOwnedByAnotherTenant() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job(TENANT_B, "foreign.pdf")));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, TENANT_A)
                .header(TenantContext.SUBJECT_ID_HEADER, SUBJECT_A)
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_RETRY)
                .exchange()
                .expectStatus().isNotFound();
    }

    private WebTestClient.RequestHeadersUriSpec<?> authorizedGet(String permission) {
        return webTestClient.get()
                .header(TenantContext.TENANT_ID_HEADER, TENANT_A)
                .header(TenantContext.SUBJECT_ID_HEADER, SUBJECT_A)
                .header(TenantContext.PERMISSIONS_HEADER, permission);
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "submitter",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3);
    }
}
