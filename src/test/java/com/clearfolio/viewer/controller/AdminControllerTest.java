package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        controller = new AdminController(conversionService, new TenantAccessService());
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsAllJobsWhenNoFilterProvided() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("b.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        job1.markDeadLettered("failed");
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredHidesMissingOrForeignJob() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "other-tenant", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(jobId, "buyer-demo", "admin", "a.pdf", "application/pdf", "hash", 100L, 3);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }

    @Test
    void getAllJobsFiltersOutForeignJobs() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "buyer-demo", "admin", "a.pdf", "application/pdf", "hash-a", 100L, 3);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "other-tenant", "admin", "b.pdf", "application/pdf", "hash-b", 100L, 3);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void deleteJobReturnsNotFoundWhenConversionServiceReturnsFalse() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class))).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDoesNotExist() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .header(TenantContext.TENANT_ID_HEADER, "buyer-demo")
                .header(TenantContext.SUBJECT_ID_HEADER, "admin")
                .header(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ADMIN_ACCESS)
                .exchange()
                .expectStatus().isNotFound();
    }
}
