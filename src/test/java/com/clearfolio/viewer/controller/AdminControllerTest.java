package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.audit.AdministrativeAuditLogger;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.security.AuditPseudonymizer;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class AdminControllerTest {

    private static final String CLAIM_SECRET = "0123456789abcdef".repeat(2);
    private static final String AUDIT_SECRET = "0123456789abcdef".repeat(2);
    private static final String TENANT_ID = "tenant-north";
    private static final String SUBJECT_ID = "administrator@example.com";

    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret(AUDIT_SECRET);
        properties.setAuditPseudonymKeyVersion("admin-v1");
        AdminController controller = new AdminController(
                conversionService,
                new TenantAccessService(CLAIM_SECRET, 300L),
                new AdministrativeAuditLogger(properties)
        );
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingClaimsAreDeniedBeforeServiceAccess() {
        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void malformedExpiredAndInvalidSignedClaimsAreDenied() {
        HttpHeaders malformed = unsignedClaimHeaders(
                TENANT_ID,
                SUBJECT_ID,
                TenantPermissions.ADMIN_READ
        );
        malformed.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, "not-an-epoch");
        malformed.set(TenantContext.CLAIMS_SIGNATURE_HEADER, "ignored");

        requestJobs(malformed).expectStatus().isUnauthorized();
        requestJobs(signedHeadersAt(
                TENANT_ID,
                SUBJECT_ID,
                Instant.now().minusSeconds(1_000L).getEpochSecond(),
                TenantPermissions.ADMIN_READ
        )).expectStatus().isUnauthorized();

        HttpHeaders invalidSignature = signedHeaders(
                TENANT_ID,
                SUBJECT_ID,
                TenantPermissions.ADMIN_READ
        );
        invalidSignature.set(TenantContext.CLAIMS_SIGNATURE_HEADER, "invalid-signature");
        requestJobs(invalidSignature).expectStatus().isUnauthorized();

        verifyNoInteractions(conversionService);
    }

    @Test
    void missingReadPermissionIsDeniedBeforeServiceAccess() {
        requestJobs(signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.JOB_READ))
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void listUsesTenantScopedServiceAndAppliesDeadLetterFilter() {
        ConversionJob deadLettered = job(TENANT_ID, "dead.pdf", true);
        ConversionJob ready = job(TENANT_ID, "ready.pdf", false);
        when(conversionService.getJobsForTenant(any(TenantContext.class)))
                .thenReturn(List.of(deadLettered, ready));
        HttpHeaders headers = signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_READ);

        requestJobs(headers)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(2)
                .jsonPath("$.jobs[0].fileName").isEqualTo("dead.pdf")
                .jsonPath("$.jobs[1].fileName").isEqualTo("ready.pdf");

        requestJobs(headers, "?deadLettered=true")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("dead.pdf");

        requestJobs(headers, "?deadLettered=false")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("ready.pdf");

        verify(conversionService, times(3)).getJobsForTenant(
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void listServiceFailureReturnsGenericInternalError() {
        when(conversionService.getJobsForTenant(any(TenantContext.class)))
                .thenThrow(new IllegalStateException("repository unavailable"));

        requestJobs(signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_READ))
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo("Unexpected error");

        verify(conversionService, never()).getAllJobs();
    }

    @Test
    void readOnlyAdministratorCannotDelete() {
        UUID jobId = UUID.randomUUID();

        webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(target -> target.addAll(signedHeaders(
                        TENANT_ID,
                        SUBJECT_ID,
                        TenantPermissions.ADMIN_READ
                )))
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(conversionService);
    }

    @Test
    void deleteConcealsMissingAndCrossTenantServiceOutcomes() {
        UUID missingId = UUID.randomUUID();
        UUID crossTenantId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(missingId), any(TenantContext.class)))
                .thenReturn(false);
        when(conversionService.deleteJob(eq(crossTenantId), any(TenantContext.class)))
                .thenReturn(false);
        HttpHeaders headers = signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE);

        deleteJob(missingId, headers).expectStatus().isNotFound();
        deleteJob(crossTenantId, headers).expectStatus().isNotFound();

        verify(conversionService).deleteJob(
                eq(missingId),
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService).deleteJob(
                eq(crossTenantId),
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService, never()).getJob(any(UUID.class));
        verify(conversionService, never()).deleteJob(any(UUID.class));
    }

    @Test
    void deleteUsesTenantScopedServiceAndReportsFailuresGenerically() {
        UUID successId = UUID.randomUUID();
        UUID failureId = UUID.randomUUID();
        when(conversionService.deleteJob(eq(successId), any(TenantContext.class)))
                .thenReturn(true);
        when(conversionService.deleteJob(eq(failureId), any(TenantContext.class)))
                .thenThrow(new IllegalStateException("delete failed"));
        HttpHeaders headers = signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE);

        deleteJob(successId, headers).expectStatus().isNoContent();
        deleteJob(failureId, headers).expectStatus().is5xxServerError();

        verify(conversionService).deleteJob(
                eq(successId),
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService).deleteJob(
                eq(failureId),
                argThat(context -> TENANT_ID.equals(context.tenantId()))
        );
        verify(conversionService, never()).getJob(any(UUID.class));
        verify(conversionService, never()).deleteJob(any(UUID.class));
    }

    @Test
    void deleteRunsBlockingLifecycleOnBoundedElasticWorker() {
        UUID jobId = UUID.randomUUID();
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        when(conversionService.deleteJob(eq(jobId), any(TenantContext.class)))
                .thenAnswer(invocation -> {
                    workerThreadName.set(Thread.currentThread().getName());
                    return true;
                });

        deleteJob(jobId, signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE))
                .expectStatus().isNoContent();

        assertTrue(workerThreadName.get().startsWith("boundedElastic-"));
    }

    @Test
    void retryAcceptedUsesDomainSeparatedActorFingerprint() {
        UUID jobId = UUID.randomUUID();
        String expectedActor = AuditPseudonymizer.forAdministrativeActor(
                AUDIT_SECRET,
                "admin-v1"
        ).fingerprint(SUBJECT_ID);
        when(conversionService.retryDeadLettered(
                eq(jobId),
                any(TenantContext.class),
                eq(expectedActor)
        )).thenReturn(RetryDeadLetterResult.ACCEPTED);

        retryJob(jobId, signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE))
                .expectStatus().isAccepted();

        verify(conversionService).retryDeadLettered(
                eq(jobId),
                argThat(context -> TENANT_ID.equals(context.tenantId())),
                eq(expectedActor)
        );
        verify(conversionService, never()).getJob(any(UUID.class));
        verify(conversionService, never()).retryDeadLettered(any(UUID.class), any(String.class));
        verify(conversionService, never()).retryDeadLettered(jobId, SUBJECT_ID);
    }

    @Test
    void retryConcealsMissingAndCrossTenantServiceOutcomes() {
        UUID missingId = UUID.randomUUID();
        UUID crossTenantId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(
                eq(missingId),
                any(TenantContext.class),
                any(String.class)
        )).thenReturn(RetryDeadLetterResult.NOT_FOUND);
        when(conversionService.retryDeadLettered(
                eq(crossTenantId),
                any(TenantContext.class),
                any(String.class)
        )).thenReturn(RetryDeadLetterResult.NOT_FOUND);
        HttpHeaders headers = signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE);

        retryJob(missingId, headers).expectStatus().isNotFound();
        retryJob(crossTenantId, headers).expectStatus().isNotFound();

        verify(conversionService).retryDeadLettered(
                eq(missingId),
                argThat(context -> TENANT_ID.equals(context.tenantId())),
                any(String.class)
        );
        verify(conversionService).retryDeadLettered(
                eq(crossTenantId),
                argThat(context -> TENANT_ID.equals(context.tenantId())),
                any(String.class)
        );
        verify(conversionService, never()).getJob(any(UUID.class));
        verify(conversionService, never()).retryDeadLettered(any(UUID.class), any(String.class));
    }

    @Test
    void retryMapsTenantScopedServiceOutcomesWithoutLeakingJobState() {
        UUID disappearedId = UUID.randomUUID();
        UUID ineligibleId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(
                eq(disappearedId),
                any(TenantContext.class),
                any(String.class)
        )).thenReturn(RetryDeadLetterResult.NOT_FOUND);
        when(conversionService.retryDeadLettered(
                eq(ineligibleId),
                any(TenantContext.class),
                any(String.class)
        )).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);
        HttpHeaders headers = signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE);

        retryJob(disappearedId, headers).expectStatus().isNotFound();
        retryJob(ineligibleId, headers).expectStatus().isEqualTo(409);
    }

    @Test
    void retryServiceFailureReturnsGenericInternalError() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(
                eq(jobId),
                any(TenantContext.class),
                any(String.class)
        )).thenThrow(new IllegalStateException("queue unavailable"));

        retryJob(jobId, signedHeaders(TENANT_ID, SUBJECT_ID, TenantPermissions.ADMIN_WRITE))
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
    }

    private WebTestClient.ResponseSpec requestJobs(HttpHeaders headers) {
        return requestJobs(headers, "");
    }

    private WebTestClient.ResponseSpec requestJobs(HttpHeaders headers, String query) {
        return webTestClient.get()
                .uri("/api/v1/admin/convert/jobs" + query)
                .headers(target -> target.addAll(headers))
                .exchange();
    }

    private WebTestClient.ResponseSpec deleteJob(UUID jobId, HttpHeaders headers) {
        return webTestClient.delete()
                .uri("/api/v1/admin/convert/jobs/" + jobId)
                .headers(target -> target.addAll(headers))
                .exchange();
    }

    private WebTestClient.ResponseSpec retryJob(UUID jobId, HttpHeaders headers) {
        return webTestClient.post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .headers(target -> target.addAll(headers))
                .exchange();
    }

    private static HttpHeaders signedHeaders(
            String tenantId,
            String subjectId,
            String... permissions
    ) {
        return signedHeadersAt(
                tenantId,
                subjectId,
                Instant.now().getEpochSecond(),
                permissions
        );
    }

    private static HttpHeaders signedHeadersAt(
            String tenantId,
            String subjectId,
            long issuedAtEpoch,
            String... permissions
    ) {
        LinkedHashSet<String> permissionSet = new LinkedHashSet<>(List.of(permissions));
        TenantContext context = new TenantContext(tenantId, subjectId, permissionSet);
        String issuedAt = Long.toString(issuedAtEpoch);
        HttpHeaders headers = unsignedClaimHeaders(tenantId, subjectId, permissions);
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, CLAIM_SECRET)
        );
        return headers;
    }

    private static HttpHeaders unsignedClaimHeaders(
            String tenantId,
            String subjectId,
            String... permissions
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, tenantId);
        headers.set(TenantContext.SUBJECT_ID_HEADER, subjectId);
        headers.set(TenantContext.PERMISSIONS_HEADER, String.join(",", permissions));
        return headers;
    }

    private static ConversionJob job(String tenantId, String fileName, boolean deadLettered) {
        return job(tenantId, fileName, deadLettered, UUID.randomUUID());
    }

    private static ConversionJob job(
            String tenantId,
            String fileName,
            boolean deadLettered,
            UUID jobId
    ) {
        ConversionJob job = new ConversionJob(
                jobId,
                tenantId,
                "owner",
                fileName,
                "application/pdf",
                "hash",
                100L,
                3
        );
        if (deadLettered) {
            job.markDeadLettered("failed");
        }
        return job;
    }
}
