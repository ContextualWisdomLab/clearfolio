package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ArtifactReadEventResponse;
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
    private ArtifactLinkService artifactLinkService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        artifactStore = mock(ArtifactStore.class);
        artifactLinkService = new ArtifactLinkService(artifactStore, "test-secret");
        ConversionController controller = new ConversionController(
                conversionService,
                new TenantAccessService(),
                artifactLinkService,
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
    void downloadRejectsJobReadWithoutArtifactReadBeforeResourceLookup() {
        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", UUID.randomUUID())
                .headers(headers -> addAuth(headers, TenantPermissions.JOB_READ))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("missing permission: " + TenantPermissions.ARTIFACT_READ);

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
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("job not found");

        verify(artifactStore, never()).getPdf(jobId);
    }

    @Test
    void downloadRejectsOwnedSucceededArtifactWithoutSignedToken() {
        UUID jobId = UUID.randomUUID();
        prepareOwnedSucceededJob(jobId);

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}/download", jobId)
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void downloadReturnsOwnedSucceededArtifactWithSignedToken() {
        UUID jobId = UUID.randomUUID();
        byte[] pdfBytes = pdfBytes();
        ConversionJob ownedJob = prepareOwnedSucceededJob(jobId, pdfBytes);
        ArtifactLinkResponse link = createLink(ownedJob);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/convert/jobs/{jobId}/download")
                        .queryParam(ArtifactLinkService.ARTIFACT_TOKEN_PARAM, tokenFrom(link))
                        .build(jobId))
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .expectBody(byte[].class).isEqualTo(pdfBytes);

        ArtifactReadEventResponse event = onlyReadEvent(jobId);
        assertEquals(200, event.statusCode());
        assertEquals(link.tokenId(), event.tokenId());
    }

    @Test
    void downloadSupportsSingleRangeAndRecordsAuditEvidence() {
        UUID jobId = UUID.randomUUID();
        byte[] pdfBytes = pdfBytes();
        ConversionJob ownedJob = prepareOwnedSucceededJob(jobId, pdfBytes);
        ArtifactLinkResponse link = createLink(ownedJob);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/convert/jobs/{jobId}/download")
                        .queryParam(ArtifactLinkService.ARTIFACT_TOKEN_PARAM, tokenFrom(link))
                        .build(jobId))
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header("X-Request-Id", "direct-download-range")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-3/9")
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .expectBody(byte[].class)
                .isEqualTo("%PDF".getBytes(StandardCharsets.US_ASCII));

        ArtifactReadEventResponse event = onlyReadEvent(jobId);
        assertEquals("bytes=0-3", event.rangeRequested());
        assertEquals(206, event.statusCode());
        assertEquals("direct-download-range", event.traceId());
    }

    @Test
    void downloadRejectsUnsatisfiableRangeAndRecordsAuditEvidence() {
        UUID jobId = UUID.randomUUID();
        byte[] pdfBytes = pdfBytes();
        ConversionJob ownedJob = prepareOwnedSucceededJob(jobId, pdfBytes);
        ArtifactLinkResponse link = createLink(ownedJob);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/convert/jobs/{jobId}/download")
                        .queryParam(ArtifactLinkService.ARTIFACT_TOKEN_PARAM, tokenFrom(link))
                        .build(jobId))
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .header(HttpHeaders.RANGE, "bytes=99-100")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */9")
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"");

        ArtifactReadEventResponse event = onlyReadEvent(jobId);
        assertEquals("bytes=99-100", event.rangeRequested());
        assertEquals(416, event.statusCode());
    }

    @Test
    void downloadRejectsRevokedSignedToken() {
        UUID jobId = UUID.randomUUID();
        ConversionJob ownedJob = prepareOwnedSucceededJob(jobId);
        ArtifactLinkResponse link = createLink(ownedJob);
        artifactLinkService.revokeLink(link.tokenId(), tenantContext(), null);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/convert/jobs/{jobId}/download")
                        .queryParam(ArtifactLinkService.ARTIFACT_TOKEN_PARAM, tokenFrom(link))
                        .build(jobId))
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_READ))
                .exchange()
                .expectStatus().isForbidden();
    }

    private ConversionJob prepareOwnedSucceededJob(UUID jobId) {
        return prepareOwnedSucceededJob(jobId, pdfBytes());
    }

    private ConversionJob prepareOwnedSucceededJob(UUID jobId, byte[] pdfBytes) {
        ConversionJob ownedJob = new ConversionJob(
                jobId,
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "hash",
                pdfBytes.length,
                3
        );
        ownedJob.markSucceeded("/artifacts/report.pdf", "conversion completed");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(ownedJob));
        when(artifactStore.getPdf(jobId)).thenReturn(Optional.of(pdfBytes));
        return ownedJob;
    }

    private ArtifactLinkResponse createLink(ConversionJob job) {
        return artifactLinkService.createLink(job, tenantContext(), ArtifactLinkRequest.viewerPreview());
    }

    private ArtifactReadEventResponse onlyReadEvent(UUID jobId) {
        var events = artifactLinkService.readEvents(jobId, tenantContext());
        assertEquals(1, events.size());
        ArtifactReadEventResponse event = events.get(0);
        assertNotNull(event.readAt());
        return event;
    }

    private static String tokenFrom(ArtifactLinkResponse response) {
        String marker = ArtifactLinkService.ARTIFACT_TOKEN_PARAM + "=";
        int markerIndex = response.artifactUrl().indexOf(marker);
        return URLDecoder.decode(
                response.artifactUrl().substring(markerIndex + marker.length()),
                StandardCharsets.UTF_8
        );
    }

    private static TenantContext tenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_READ)
        );
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static void addAuth(HttpHeaders headers, String permissions) {
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
    }
}
