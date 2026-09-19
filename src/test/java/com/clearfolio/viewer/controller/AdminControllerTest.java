package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    private static final String TENANT_ID = "tenant-1";
    private static final String SUBJECT_ID = "sub-1";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private TenantContext readContext;
    private TenantContext writeContext;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        readContext = new TenantContext(TENANT_ID, SUBJECT_ID, Set.of(TenantPermissions.ADMIN_READ));
        writeContext = new TenantContext(TENANT_ID, SUBJECT_ID, Set.of(TenantPermissions.ADMIN_WRITE));
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private AdminController controller;

    @Test
    void getAllJobsReturnsTenantScopedJobsWhenNoFilterProvided() {
        ConversionJob job1 = tenantJob("a.pdf", "hash-a");
        ConversionJob job2 = tenantJob("b.pdf", "hash-b");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(readContext);
        when(conversionService.getAllJobs(readContext)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
        verify(conversionService).getAllJobs(readContext);
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = tenantJob("a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = tenantJob("b.pdf", "hash-b");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(readContext);
        when(conversionService.getAllJobs(readContext)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
        verify(conversionService).getAllJobs(readContext);
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = tenantJob("a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = tenantJob("b.pdf", "hash-b");
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(readContext);
        when(conversionService.getAllJobs(readContext)).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ));
        verify(conversionService).getAllJobs(readContext);
    }

    @Test
    void deleteJobReturnsNoContentForOwnedJob() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(writeContext);
        when(conversionService.deleteJob(jobId, writeContext)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
        verify(conversionService).deleteJob(jobId, writeContext);
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(writeContext);
        when(conversionService.deleteJob(jobId, writeContext)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(jobId, writeContext);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenOwnedJobIsAccepted() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(writeContext);
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, writeContext))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(tenantAccessService).require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE));
        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, writeContext);
    }

    @Test
    void retryDeadLetteredHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(writeContext);
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, writeContext))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, writeContext);
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenOwnedJobIsNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(writeContext);
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, writeContext))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, writeContext);
    }

    private static ConversionJob tenantJob(String fileName, String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                TENANT_ID,
                SUBJECT_ID,
                fileName,
                "application/pdf",
                contentHash,
                100L,
                3);
    }
}
