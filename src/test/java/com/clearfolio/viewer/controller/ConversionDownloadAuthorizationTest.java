package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * Security regressions for tenant-scoped direct conversion artifact downloads.
 */
class ConversionDownloadAuthorizationTest {

    private DocumentConversionService conversionService;
    private ArtifactStore artifactStore;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        artifactStore = mock(ArtifactStore.class);
        ConversionController controller = new ConversionController(
                conversionService,
                new TenantAccessService(),
                new ArtifactLinkService(artifactStore, "test-secret"),
                artifactStore,
                DataSize.ofBytes(262_144L)
        );
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void downloadRejectsMissingTenantClaimsBeforeResourceLookup() {
        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("auth token required");

        verifyNoInteractions(conversionService, artifactStore);
    }

    @Test
    void downloadRejectsMissingReadPermissionBeforeResourceLookup() {
        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", UUID.randomUUID())
                .headers(headers -> addAuth(headers, TenantPermissions.VIEWER_READ))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("missing permission: " + TenantPermissions.JOB_READ);

        verifyNoInteractions(conversionService, artifactStore);
    }

    @Test
    void downloadConcealsCrossTenantJobBeforeArtifactLookup() {
        UUID jobId = UUID.randomUUID();
        ConversionJob foreignJob = new ConversionJob(
                jobId,
                "tenant-b",
                "subject-b",
                "confidential.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hash",
                12L,
                3
        );
        foreignJob.markSucceeded("/artifacts/confidential.pdf", "conversion completed");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(foreignJob));

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("job not found");

        verify(artifactStore, never()).getPdf(jobId);
    }

    @Test
    void downloadReturnsOwnedSucceededArtifactWithReadPermission() {
        UUID jobId = UUID.randomUUID();
        byte[] pdfBytes = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ConversionJob ownedJob = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hash",
                12L
        );
        ownedJob.markSucceeded("/artifacts/report.pdf", "conversion completed");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(ownedJob));
        when(artifactStore.getPdf(jobId)).thenReturn(Optional.of(pdfBytes));

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .expectBody(byte[].class).isEqualTo(pdfBytes);
    }

    private static void addAuth(HttpHeaders headers, String permissions) {
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
    }
}
