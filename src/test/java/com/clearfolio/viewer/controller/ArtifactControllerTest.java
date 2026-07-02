package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ArtifactLinkRevocationRequest;
import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

class ArtifactControllerTest {

    private DocumentConversionService conversionService;
    private ArtifactStore artifactStore;
    private ArtifactLinkService artifactLinkService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        artifactStore = new InMemoryArtifactStore();
        artifactLinkService = new ArtifactLinkService(artifactStore, "test-secret");
        ArtifactController controller = new ArtifactController(
                conversionService,
                artifactStore,
                artifactLinkService,
                new TenantAccessService()
        );
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void returnsNotFoundWhenJobMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundWhenJobIsNotSucceeded() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundWhenArtifactIsMissing() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createsSignedArtifactLinkForSameTenantJob() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.post()
                .uri("/api/v1/viewer/{docId}/artifact-links", docId)
                .headers(ArtifactControllerTest::addArtifactLinkPermission)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ArtifactLinkRequest("download", 120, "viewer-session-1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.artifactUrl").value(value -> assertSignedArtifactUrl((String) value, docId))
                .jsonPath("$.tokenId").value(value -> assertTrue(((String) value).length() > 10))
                .jsonPath("$.scope").isEqualTo(ArtifactLinkService.ARTIFACT_READ_SCOPE)
                .jsonPath("$.docId").isEqualTo(docId.toString());
    }

    @Test
    void createArtifactLinkRequiresPermission() {
        UUID docId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/viewer/{docId}/artifact-links", docId)
                .headers(headers -> addAuth(headers, TenantPermissions.VIEWER_READ))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createArtifactLinkReturnsNotFoundWhenJobIsMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.post()
                .uri("/api/v1/viewer/{docId}/artifact-links", docId)
                .headers(ArtifactControllerTest::addArtifactLinkPermission)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createArtifactLinkHidesCrossTenantJob() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId, "other-tenant");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.post()
                .uri("/api/v1/viewer/{docId}/artifact-links", docId)
                .headers(ArtifactControllerTest::addArtifactLinkPermission)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void revokeArtifactLinkRequiresPermission() {
        webTestClient.post()
                .uri("/api/v1/viewer/artifact-links/{tokenId}/revoke", "token-1")
                .headers(headers -> addAuth(headers, TenantPermissions.ARTIFACT_LINK_CREATE))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void revokeArtifactLinkDeniesFutureArtifactReads() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = signedArtifactLink(job);

        webTestClient.post()
                .uri("/api/v1/viewer/artifact-links/{tokenId}/revoke", link.tokenId())
                .headers(ArtifactControllerTest::addRevokePermission)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ArtifactLinkRevocationRequest("shared outside tenant"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenId").isEqualTo(link.tokenId())
                .jsonPath("$.revoked").isEqualTo(true)
                .jsonPath("$.revokedBy").isEqualTo(TenantContext.DEMO_SUBJECT_ID)
                .jsonPath("$.reason").isEqualTo("shared outside tenant");

        webTestClient.get()
                .uri(link.artifactUrl())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void revokeArtifactLinkReturnsNotFoundForUnknownOrCrossTenantToken() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = signedArtifactLink(job);

        webTestClient.post()
                .uri("/api/v1/viewer/artifact-links/{tokenId}/revoke", "missing-token")
                .headers(ArtifactControllerTest::addRevokePermission)
                .exchange()
                .expectStatus().isNotFound();
        webTestClient.post()
                .uri("/api/v1/viewer/artifact-links/{tokenId}/revoke", link.tokenId())
                .headers(headers -> {
                    headers.add(TenantContext.TENANT_ID_HEADER, "other-tenant");
                    headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
                    headers.add(TenantContext.PERMISSIONS_HEADER, TenantPermissions.ARTIFACT_LINK_REVOKE);
                })
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listArtifactReadEventsRequiresPermission() {
        UUID docId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}/artifact-read-events", docId)
                .headers(headers -> addAuth(headers, TenantPermissions.VIEWER_READ))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listsArtifactReadEventsForSuccessfulAndUnsatisfiableReads() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);
        String artifactUrl = signedArtifactUrl(job);

        webTestClient.get()
                .uri(artifactUrl)
                .header("X-Request-Id", "trace-ok")
                .exchange()
                .expectStatus().isOk();
        webTestClient.get()
                .uri(artifactUrl)
                .header(HttpHeaders.RANGE, "bytes=100-200")
                .header("X-Request-Id", "trace-416")
                .exchange()
                .expectStatus().isEqualTo(416);

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}/artifact-read-events", docId)
                .headers(ArtifactControllerTest::addAuditPermission)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].tenantId").isEqualTo(TenantContext.DEMO_TENANT_ID)
                .jsonPath("$[0].docId").isEqualTo(docId.toString())
                .jsonPath("$[0].statusCode").isEqualTo(200)
                .jsonPath("$[0].traceId").isEqualTo("trace-ok")
                .jsonPath("$[1].rangeRequested").isEqualTo("bytes=100-200")
                .jsonPath("$[1].statusCode").isEqualTo(416)
                .jsonPath("$[1].traceId").isEqualTo("trace-416");
    }

    @Test
    void returnsUnauthorizedWhenArtifactTokenIsMissing() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void returnsFullPdfWhenNoRangeHeader() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PDF)
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returnsFullPdfWhenTokenIsSuppliedAsBearerHeader() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);
        String token = artifactTokenFrom(signedArtifactUrl(job));

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returnsFullPdfWhenRangeHeaderIsBlank() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "   ")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returnsPartialPdfForExplicitRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {0, 1, 2, 3});
    }

    @Test
    void returnsPartialPdfForOpenEndedRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=7-")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {7, 8, 9});
    }

    @Test
    void returnsPartialPdfForSuffixRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=-3")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {7, 8, 9});
    }

    @Test
    void returns416ForUnsatisfiableRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=100-200")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForInvalidMultipleRanges() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=0-1,2-3")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForInvalidRangeUnit() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "items=0-1")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForEmptyRangeSpec() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeHasNoDash() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=123")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeStartIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=a-3")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeEndIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=0-b")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeEndIsBeforeStart() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=5-2")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void boundsRangeEndToPdfLength() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=0-999")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-9/10")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returns416WhenSuffixRangeIsMissingLength() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=-")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenSuffixRangeIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=-abc")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenSuffixRangeIsZero() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=-0")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returnsFullBodyForSuffixRangeLongerThanPdf() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri(signedArtifactUrl(job))
                .header(HttpHeaders.RANGE, "bytes=-999")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-9/10")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    private String signedArtifactUrl(ConversionJob job) {
        return signedArtifactLink(job).artifactUrl();
    }

    private ArtifactLinkResponse signedArtifactLink(ConversionJob job) {
        return artifactLinkService.createLink(job, tenantContext(), ArtifactLinkRequest.viewerPreview());
    }

    private static String artifactTokenFrom(String artifactUrl) {
        return artifactUrl.substring(artifactUrl.indexOf(ArtifactLinkService.ARTIFACT_TOKEN_PARAM + "=")
                + ArtifactLinkService.ARTIFACT_TOKEN_PARAM.length() + 1);
    }

    private static TenantContext tenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_LINK_CREATE, TenantPermissions.VIEWER_READ)
        );
    }

    private static void addArtifactLinkPermission(HttpHeaders headers) {
        addAuth(headers, TenantPermissions.ARTIFACT_LINK_CREATE);
    }

    private static void addRevokePermission(HttpHeaders headers) {
        addAuth(headers, TenantPermissions.ARTIFACT_LINK_REVOKE);
    }

    private static void addAuditPermission(HttpHeaders headers) {
        addAuth(headers, TenantPermissions.AUDIT_READ);
    }

    private static void addAuth(HttpHeaders headers, String permissions) {
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
    }

    private static void assertSignedArtifactUrl(String actual, UUID docId) {
        assertTrue(actual.startsWith("/artifacts/" + docId + ".pdf?artifactToken="));
    }

    private static ConversionJob succeededJob(UUID docId) {
        return succeededJob(docId, TenantContext.DEMO_TENANT_ID);
    }

    private static ConversionJob succeededJob(UUID docId, String tenantId) {
        ConversionJob job = new ConversionJob(
                docId,
                tenantId,
                TenantContext.DEMO_SUBJECT_ID,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("/artifacts/" + docId + ".pdf", "done");
        return job;
    }

    private static byte[] sampleBytes() {
        return new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    }
}
