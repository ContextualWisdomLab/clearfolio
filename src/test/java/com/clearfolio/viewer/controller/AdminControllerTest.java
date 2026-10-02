package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private static final TenantContext ADMIN_CONTEXT = new TenantContext(
            "admin-tenant",
            "admin-user",
            Set.of(TenantPermissions.ADMIN_READ, TenantPermissions.ADMIN_WRITE)
    );

    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);
        when(tenantAccessService.requireSigned(any(), any())).thenReturn(ADMIN_CONTEXT);
        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getAllJobsReturnsOnlyRequestTenantJobsWhenNoFilterProvided() {
        ConversionJob job1 = tenantJob("admin-tenant", "a.pdf", "hash-a");
        ConversionJob job2 = tenantJob("other-tenant", "b.pdf", "hash-b");
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
        verify(tenantAccessService).requireSigned(any(), eq(TenantPermissions.ADMIN_READ));
    }

    @Test
    void getAllJobsFiltersByDeadLetteredTrue() {
        ConversionJob job1 = tenantJob("admin-tenant", "a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = tenantJob("admin-tenant", "b.pdf", "hash-b");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void getAllJobsFiltersByDeadLetteredFalse() {
        ConversionJob job1 = tenantJob("admin-tenant", "a.pdf", "hash-a");
        job1.markDeadLettered("failed");
        ConversionJob job2 = tenantJob("admin-tenant", "b.pdf", "hash-b");

        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs?deadLettered=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("b.pdf");
    }

    @Test
    void deleteJobReturnsNoContent() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, ADMIN_CONTEXT)).thenReturn(true);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNoContent();
        verify(conversionService).deleteJob(jobId, ADMIN_CONTEXT);
    }

    @Test
    void deleteJobHidesMissingOrCrossTenantJobAsNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.deleteJob(jobId, ADMIN_CONTEXT)).thenReturn(false);

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).deleteJob(jobId);
    }

    @Test
    void retryDeadLetteredReturnsAcceptedWhenAccepted() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob("admin-tenant", "a.pdf", "hash-a");
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin-user")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenNotFound() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.empty());

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobDisappearsAfterOwnershipCheck() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob("admin-tenant", "a.pdf", "hash-a");
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin-user"))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void retryDeadLetteredReturnsConflictWhenNotEligible() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob("admin-tenant", "a.pdf", "hash-a");
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin-user"))
                .thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isEqualTo(409); // isConflict() isn't always available depending on spring-test version, so using isEqualTo(409) is safer
    }

    @Test
    void retryDeadLetteredHidesCrossTenantJobBeforeMutation() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = tenantJob("other-tenant", "a.pdf", "hash-a");
        when(conversionService.getJob(jobId)).thenReturn(java.util.Optional.of(job));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"))
                .when(tenantAccessService).requireSameTenant(ADMIN_CONTEXT, job);

        webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();

        verify(conversionService, never()).retryDeadLettered(any(), any());
    }

    @Test
    void deniedAdminReadDoesNotReachConversionService() {
        when(tenantAccessService.requireSigned(any(), eq(TenantPermissions.ADMIN_READ)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "missing permission"));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deniedAdminWriteDoesNotReachConversionService() {
        UUID jobId = UUID.randomUUID();
        when(tenantAccessService.requireSigned(any(), eq(TenantPermissions.ADMIN_WRITE)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "missing permission"));

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    private static ConversionJob tenantJob(String tenantId, String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "owner",
                fileName,
                "application/pdf",
                hash,
                100L,
                3
        );
    }
}
