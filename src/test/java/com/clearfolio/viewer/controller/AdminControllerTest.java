package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
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
    private static final String SUBJECT_ID = "subject-1";

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        when(tenantAccessService.require(any(HttpHeaders.class), any())).thenReturn(
                new TenantContext(TENANT_ID, SUBJECT_ID, Set.of(
                        TenantPermissions.ADMIN_READ,
                        TenantPermissions.ADMIN_DELETE,
                        TenantPermissions.ADMIN_RETRY
                ))
        );
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsOwnedJobsWhenNoFilterProvided() {
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsDoesNotExposeAnotherTenantsJobs() {
        ConversionJob owned = jobForTenant(TENANT_ID, "owned.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(owned, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("owned.pdf");
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredTrue() {
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");
        foreign.markDeadLettered("failed");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersOwnedJobsByDeadLetteredFalse() {
        ConversionJob job1 = jobForTenant(TENANT_ID, "a.pdf");
        job1.markDeadLettered("failed");
        ConversionJob job2 = jobForTenant(TENANT_ID, "b.pdf");
        ConversionJob foreign = jobForTenant("tenant-2", "foreign.pdf");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2, foreign));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContentForOwnedJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(eq(jobId), any(TenantContext.class));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedForOwnedJobAndAttributesSubject() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredHidesAnotherTenantsJob() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant("tenant-2", "foreign.pdf")));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(eq(jobId), any());
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenOwnedJobDisappearsBeforeRetry() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenOwnedJobIsNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(jobForTenant(TENANT_ID, "owned.pdf")));
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID)).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static ConversionJob jobForTenant(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "job-subject",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3
        );
    }
}
