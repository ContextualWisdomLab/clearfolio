package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static final String TENANT_ID = "tenant-a";
    private static final String SUBJECT_ID = "operator-a";

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        AdminController controller = new AdminController(
                conversionService,
                new TenantAccessService()
        );
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsTenantScopedJobsWhenNoFilterProvided() {
        TenantContext context = tenantContext();
        ConversionJob job1 = tenantJob("a.pdf", "hash-a");
        ConversionJob job2 = tenantJob("b.pdf", "hash-b");
        when(conversionService.getAllJobs(eq(context))).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");

        verify(conversionService).getAllJobs(eq(context));
        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void getAllJobsFiltersTenantScopedDeadLetteredJobs() {
        TenantContext context = tenantContext();
        ConversionJob deadLetteredJob = tenantJob("dead.pdf", "hash-dead");
        deadLetteredJob.markDeadLettered("failed");
        ConversionJob activeJob = tenantJob("active.pdf", "hash-active");
        when(conversionService.getAllJobs(eq(context))).thenReturn(
                Arrays.asList(deadLetteredJob, activeJob)
        );

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("dead.pdf");

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("active.pdf");

        verify(conversionService, org.mockito.Mockito.times(2)).getAllJobs(eq(context));
        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void deleteJobUsesTenantScopedMutationAndReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = tenantContext();
        when(conversionService.deleteJob(eq(jobId), eq(context))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isNoContent();

        verify(conversionService).deleteJob(eq(jobId), eq(context));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void deleteJobConcealsMissingOrForeignJobsAsNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = tenantContext();
        when(conversionService.deleteJob(eq(jobId), eq(context))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).deleteJob(eq(jobId), eq(context));
        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredUsesTenantScopedMutationWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = tenantContext();
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, context))
                .thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isAccepted();

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, context);
        verify(conversionService, never()).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredConcealsMissingOrForeignJobsAsNotFound() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = tenantContext();
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, context))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, context);
        verify(conversionService, never()).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenOwnedJobIsNotEligible() {
        UUID jobId = UUID.randomUUID();
        TenantContext context = tenantContext();
        when(conversionService.retryDeadLettered(jobId, SUBJECT_ID, context))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(AdminControllerTest::addAdminHeaders)
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(conversionService).retryDeadLettered(jobId, SUBJECT_ID, context);
        verify(conversionService, never()).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void everyAdminEndpointRejectsMissingClaimsBeforeServiceAccess() {
        UUID jobId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void everyAdminEndpointRequiresTenantConfigurePermission() {
        UUID jobId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> addHeaders(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(headers -> addHeaders(headers, TenantPermissions.JOB_DELETE))
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(headers -> addHeaders(headers, TenantPermissions.JOB_RETRY))
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void blankTenantOrSubjectClaimsFailClosedBeforeServiceAccess() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> {
                    headers.set(TenantContext.TENANT_ID_HEADER, " ");
                    headers.set(TenantContext.SUBJECT_ID_HEADER, SUBJECT_ID);
                    headers.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.TENANT_CONFIGURE);
                })
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .headers(headers -> {
                    headers.set(TenantContext.TENANT_ID_HEADER, TENANT_ID);
                    headers.set(TenantContext.SUBJECT_ID_HEADER, " ");
                    headers.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.TENANT_CONFIGURE);
                })
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
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
                3
        );
    }

    private static TenantContext tenantContext() {
        return new TenantContext(
                TENANT_ID,
                SUBJECT_ID,
                Set.of(TenantPermissions.TENANT_CONFIGURE)
        );
    }

    private static void addAdminHeaders(HttpHeaders headers) {
        addHeaders(headers, TenantPermissions.TENANT_CONFIGURE);
    }

    private static void addHeaders(HttpHeaders headers, String permission) {
        headers.set(TenantContext.TENANT_ID_HEADER, TENANT_ID);
        headers.set(TenantContext.SUBJECT_ID_HEADER, SUBJECT_ID);
        headers.set(TenantContext.PERMISSIONS_HEADER, permission);
    }
}
