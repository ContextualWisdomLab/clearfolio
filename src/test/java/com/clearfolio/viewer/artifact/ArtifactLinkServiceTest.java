package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkRevocationRequest;
import com.clearfolio.viewer.api.ArtifactLinkRevocationResponse;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ArtifactReadEventResponse;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;

class ArtifactLinkServiceTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

    private InMemoryArtifactStore artifactStore;
    private ArtifactLinkService service;

    @BeforeEach
    void setUp() {
        artifactStore = new InMemoryArtifactStore();
        service = serviceAt(NOW);
    }

    @Test
    void createsAndVerifiesDefaultViewerPreviewLink() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        ArtifactLinkResponse response = service.createLink(job, tenantContext(), null);

        assertTrue(response.artifactUrl().startsWith("/artifacts/" + docId + ".pdf?artifactToken="));
        assertEquals(NOW.plusSeconds(300), response.expiresAt());
        assertEquals(ArtifactLinkService.ARTIFACT_READ_SCOPE, response.scope());
        assertEquals(docId.toString(), response.docId());
        assertDoesNotThrow(() -> service.verifyReadToken(docId, job, pdf, tokenFrom(response)));
    }

    @Test
    void createsLinkWithRequestedTtlAndCapsLargeTtl() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());

        ArtifactLinkResponse shortLink = service.createLink(
                job,
                tenantContext(),
                new ArtifactLinkRequest("download", 60, "viewer-1")
        );
        ArtifactLinkResponse cappedLink = service.createLink(
                job,
                tenantContext(),
                new ArtifactLinkRequest("download", 9_999, "viewer-1")
        );

        assertEquals(NOW.plusSeconds(60), shortLink.expiresAt());
        assertEquals(NOW.plusSeconds(900), cappedLink.expiresAt());
    }

    @Test
    void createLinkDefaultsInvalidTtlAndBlankPurpose() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());

        ArtifactLinkResponse response = service.createLink(
                job,
                tenantContext(),
                new ArtifactLinkRequest(" \u0000 ", -1, null)
        );

        assertEquals(NOW.plusSeconds(300), response.expiresAt());
        assertDoesNotThrow(() -> service.verifyReadToken(docId, job, sampleBytes(), tokenFrom(response)));
    }

    @Test
    void createsAndVerifiesTokenWhenSecretIsGeneratedForDemoRuntime() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkService generatedSecretService = new ArtifactLinkService(
                artifactStore,
                " ",
                Clock.fixed(NOW, ZoneOffset.UTC),
                new FixedSecureRandom()
        );

        ArtifactLinkResponse response = generatedSecretService.createLink(job, tenantContext(), null);

        assertDoesNotThrow(() -> generatedSecretService.verifyReadToken(
                docId,
                job,
                sampleBytes(),
                tokenFrom(response)
        ));
    }

    @Test
    void createsAndVerifiesTokenWhenConfiguredSecretIsNull() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkService generatedSecretService = new ArtifactLinkService(
                artifactStore,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new FixedSecureRandom()
        );

        ArtifactLinkResponse response = generatedSecretService.createLink(job, tenantContext(), null);

        assertDoesNotThrow(() -> generatedSecretService.verifyReadToken(
                docId,
                job,
                sampleBytes(),
                tokenFrom(response)
        ));
    }

    @Test
    void springConstructorUsesProvidedLedger() {
        ArtifactLinkLedger ledger = new ArtifactLinkLedger();
        ArtifactLinkService serviceWithLedger = new ArtifactLinkService(artifactStore, ledger, SECRET);
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());

        ArtifactLinkResponse response = serviceWithLedger.createLink(job, tenantContext(), null);

        assertTrue(ledger.findByTokenId(response.tokenId()).isPresent());
    }

    @Test
    void createLinkRejectsNullJob() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createLink(null, tenantContext(), null)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createLinkRejectsNullTenantContext() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createLink(job, null, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createLinkRejectsCrossTenantJob() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId, "other-tenant");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createLink(job, tenantContext(), null)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createLinkRejectsUnfinishedJob() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createLink(job, tenantContext(), null)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createLinkRejectsMissingArtifactBytes() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createLink(job, tenantContext(), null)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void verifyReadTokenRejectsMissingToken() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.verifyReadToken(docId, job, sampleBytes(), null)
        );
    }

    @Test
    void verifyReadTokenRejectsBlankToken() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.verifyReadToken(docId, job, sampleBytes(), "   ")
        );
    }

    @Test
    void verifyReadTokenRejectsInvalidShape() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.verifyReadToken(docId, job, sampleBytes(), "not-a-token")
        );
    }

    @Test
    void verifyReadTokenRejectsInvalidSignature() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.verifyReadToken(docId, job, sampleBytes(), token + "x")
        );
    }

    @Test
    void verifyReadTokenRejectsExpiredToken() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(
                job,
                tenantContext(),
                new ArtifactLinkRequest("viewer-preview", 1, null)
        ));
        ArtifactLinkService laterService = serviceAt(NOW.plusSeconds(2));

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> laterService.verifyReadToken(docId, job, sampleBytes(), token)
        );
    }

    @Test
    void verifyReadTokenRejectsWrongScope() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));
        String wrongScopeToken = retokenize(token, 5, "artifact:write");

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(docId, job, sampleBytes(), wrongScopeToken)
        );
    }

    @Test
    void verifyReadTokenRejectsWrongDocumentBinding() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(UUID.randomUUID(), job, sampleBytes(), token)
        );
    }

    @Test
    void verifyReadTokenRejectsWrongTenantBinding() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        ConversionJob otherTenantJob = succeededJob(docId, "other-tenant");
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(docId, otherTenantJob, sampleBytes(), token)
        );
    }

    @Test
    void verifyReadTokenRejectsNullJobBinding() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(docId, null, sampleBytes(), token)
        );
    }

    @Test
    void verifyReadTokenRejectsChangedArtifactBytes() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(docId, job, new byte[] {9, 9, 9}, token)
        );
    }

    @Test
    void verifyReadTokenRejectsUnknownLedgerToken() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));
        ArtifactLinkService emptyLedgerService = serviceAt(NOW);

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> emptyLedgerService.verifyReadToken(docId, job, sampleBytes(), token)
        );
    }

    @Test
    void verifyReadTokenRejectsLedgerTenantMismatch() {
        assertLedgerMismatch(record -> copyRecord(record, "other-tenant", record.docId(), record.artifactChecksum()));
    }

    @Test
    void verifyReadTokenRejectsLedgerDocMismatch() {
        assertLedgerMismatch(record -> copyRecord(record, record.tenantId(), UUID.randomUUID(), record.artifactChecksum()));
    }

    @Test
    void verifyReadTokenRejectsLedgerChecksumMismatch() {
        assertLedgerMismatch(record -> copyRecord(record, record.tenantId(), record.docId(), "different-checksum"));
    }

    @Test
    void revokeLinkDeniesFutureReadsAndIsIdempotent() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = service.createLink(job, tenantContext(), null);

        ArtifactLinkRevocationResponse first = service.revokeLink(
                link.tokenId(),
                revokeTenantContext(),
                new ArtifactLinkRevocationRequest("viewer closed")
        );
        ArtifactLinkRevocationResponse second = service.revokeLink(
                link.tokenId(),
                revokeTenantContext(),
                new ArtifactLinkRevocationRequest("second request")
        );

        assertEquals(link.tokenId(), first.tokenId());
        assertEquals(NOW, first.revokedAt());
        assertEquals(TenantContext.DEMO_SUBJECT_ID, first.revokedBy());
        assertEquals("viewer closed", first.reason());
        assertTrue(first.revoked());
        assertEquals(first, second);
        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> service.verifyReadToken(docId, job, sampleBytes(), tokenFrom(link))
        );
    }

    @Test
    void revokeLinkDefaultsReasonWhenRequestIsNull() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = service.createLink(job, tenantContext(), null);

        ArtifactLinkRevocationResponse revoked = service.revokeLink(link.tokenId(), revokeTenantContext(), null);

        assertEquals("operator-request", revoked.reason());
    }

    @Test
    void revokeLinkDefaultsBlankReasonAndRejectsUnknownOrWrongTenantLinks() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = service.createLink(job, tenantContext(), null);

        ArtifactLinkRevocationResponse revoked = service.revokeLink(
                link.tokenId(),
                revokeTenantContext(),
                new ArtifactLinkRevocationRequest(" \u0000 ")
        );

        assertEquals("operator-request", revoked.reason());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class,
                () -> service.revokeLink(" ", revokeTenantContext(), null)
        ).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class,
                () -> service.revokeLink("missing-token", revokeTenantContext(), null)
        ).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class,
                () -> service.revokeLink(link.tokenId(), otherTenantContext(), null)
        ).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class,
                () -> service.revokeLink(link.tokenId(), null, null)
        ).getStatusCode());
    }

    @Test
    void recordReadReturnsTenantScopedEvents() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactTokenClaims claims = service.verifyReadToken(
                docId,
                job,
                sampleBytes(),
                tokenFrom(service.createLink(
                        job,
                        tenantContext(),
                        new ArtifactLinkRequest("viewer", 300, " viewer-1 ")
                ))
        );

        service.recordRead(claims, " bytes=0-3 ", 206, " trace-1 ");

        List<ArtifactReadEventResponse> events = service.readEvents(docId, auditTenantContext());
        assertEquals(1, events.size());
        ArtifactReadEventResponse event = events.getFirst();
        assertEquals(TenantContext.DEMO_TENANT_ID, event.tenantId());
        assertEquals(TenantContext.DEMO_SUBJECT_ID, event.subjectId());
        assertEquals(docId.toString(), event.docId());
        assertEquals(claims.tokenId(), event.tokenId());
        assertEquals("bytes=0-3", event.rangeRequested());
        assertEquals(206, event.statusCode());
        assertEquals("trace-1", event.traceId());
        assertEquals(NOW, event.readAt());
        assertTrue(service.readEvents(docId, otherTenantContext()).isEmpty());
        assertTrue(service.readEvents(UUID.randomUUID(), auditTenantContext()).isEmpty());
    }

    @Test
    void verifyReadTokenRejectsUnsupportedVersion() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        String token = tokenFrom(service.createLink(job, tenantContext(), null));
        String wrongVersionToken = retokenize(token, 0, "v2");

        assertTokenStatus(
                HttpStatus.UNAUTHORIZED,
                () -> service.verifyReadToken(docId, job, sampleBytes(), wrongVersionToken)
        );
    }

    @Test
    void createLinkThrowsWhenSha256DigestIsUnavailable() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.createLink(job, tenantContext(), null)
            );
            assertEquals("SHA-256 digest unavailable", ex.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }
    }

    @Test
    void createLinkThrowsWhenHmacSigningFails() throws Exception {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        Field signingKey = ArtifactLinkService.class.getDeclaredField("signingKey");
        signingKey.setAccessible(true);
        signingKey.set(service, null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.createLink(job, tenantContext(), null)
        );

        assertEquals("artifact token signing failed", ex.getMessage());
    }

    @Test
    void resolveTokenPrefersQueryToken() {
        assertEquals("query-token", ArtifactLinkService.resolveToken(" query-token ", "Bearer bearer-token"));
    }

    @Test
    void resolveTokenReadsBearerToken() {
        assertEquals("bearer-token", ArtifactLinkService.resolveToken(null, "Bearer bearer-token "));
    }

    @Test
    void resolveTokenUsesBearerTokenWhenQueryTokenIsBlank() {
        assertEquals("bearer-token", ArtifactLinkService.resolveToken(" ", "Bearer bearer-token"));
    }

    @Test
    void resolveTokenReturnsNullForMissingOrUnsupportedHeaders() {
        assertNull(ArtifactLinkService.resolveToken(null, null));
        assertNull(ArtifactLinkService.resolveToken(null, "   "));
        assertNull(ArtifactLinkService.resolveToken(null, "Basic abc"));
        assertNull(ArtifactLinkService.resolveToken(null, "Bearer   "));
    }

    private ArtifactLinkService serviceAt(Instant instant) {
        return new ArtifactLinkService(
                artifactStore,
                SECRET,
                Clock.fixed(instant, ZoneOffset.UTC),
                new FixedSecureRandom()
        );
    }

    private ArtifactLinkService serviceAt(ArtifactLinkLedger ledger, Instant instant) {
        return new ArtifactLinkService(
                artifactStore,
                ledger,
                SECRET,
                Clock.fixed(instant, ZoneOffset.UTC),
                new FixedSecureRandom()
        );
    }

    private void assertLedgerMismatch(UnaryOperator<ArtifactLinkRecord> mutation) {
        ArtifactLinkLedger ledger = new ArtifactLinkLedger();
        ArtifactLinkService ledgerService = serviceAt(ledger, NOW);
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        artifactStore.putPdf(docId, sampleBytes());
        ArtifactLinkResponse link = ledgerService.createLink(job, tenantContext(), null);
        ArtifactLinkRecord record = ledger.findByTokenId(link.tokenId()).orElseThrow();
        ledger.recordIssued(mutation.apply(record));

        assertTokenStatus(
                HttpStatus.FORBIDDEN,
                () -> ledgerService.verifyReadToken(docId, job, sampleBytes(), tokenFrom(link))
        );
    }

    private static ArtifactLinkRecord copyRecord(
            ArtifactLinkRecord record,
            String tenantId,
            UUID docId,
            String artifactChecksum) {
        return new ArtifactLinkRecord(
                record.tokenId(),
                tenantId,
                record.subjectId(),
                docId,
                record.scope(),
                record.purpose(),
                artifactChecksum,
                record.viewerSessionId(),
                record.issuedAt(),
                record.expiresAt(),
                record.revokedAt(),
                record.revokedBy(),
                record.revokeReason()
        );
    }

    private static void assertTokenStatus(HttpStatus expected, ThrowingRunnable runnable) {
        ArtifactTokenException ex = assertThrows(ArtifactTokenException.class, runnable::run);
        assertEquals(expected, ex.getStatus());
    }

    private static String tokenFrom(ArtifactLinkResponse response) {
        String param = ArtifactLinkService.ARTIFACT_TOKEN_PARAM + "=";
        return response.artifactUrl().substring(response.artifactUrl().indexOf(param) + param.length());
    }

    private static String retokenize(String token, int fieldIndex, String value) {
        String[] parts = token.split("\\.");
        parts[fieldIndex] = encode(value);
        String payload = String.join(".", Arrays.copyOf(parts, 10));
        return payload + "." + hmac(payload);
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static TenantContext tenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_LINK_CREATE, TenantPermissions.VIEWER_READ)
        );
    }

    private static TenantContext revokeTenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_LINK_REVOKE)
        );
    }

    private static TenantContext auditTenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.AUDIT_READ)
        );
    }

    private static TenantContext otherTenantContext() {
        return new TenantContext(
                "other-tenant",
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_LINK_REVOKE, TenantPermissions.AUDIT_READ)
        );
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private byte nextByte = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = nextByte++;
            }
        }
    }
}
