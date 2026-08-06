package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies fail-closed signed artifact-token parsing and claim validation.
 */
class ArtifactTokenBoundaryTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    private InMemoryArtifactStore artifactStore;
    private ArtifactLinkService service;
    private UUID documentId;
    private ConversionJob conversionJob;
    private byte[] artifactBytes;
    private String[] validPayloadFields;

    @BeforeEach
    void setUp() {
        artifactStore = new InMemoryArtifactStore();
        service = new ArtifactLinkService(
                artifactStore,
                SECRET,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new FixedSecureRandom()
        );
        documentId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        conversionJob = succeededJob(documentId);
        artifactBytes = new byte[] {0, 1, 2, 3};
        artifactStore.putPdf(documentId, artifactBytes);
        ArtifactLinkResponse link = service.createLink(conversionJob, tenantContext(), null);
        String[] tokenParts = tokenFrom(link).split("\\.", -1);
        assertEquals(11, tokenParts.length, "a valid token must contain ten payload fields and one signature");
        validPayloadFields = Arrays.copyOf(tokenParts, 10);
    }

    @Test
    void rejectsSignedPayloadWithOnlyNineFields() {
        assertMalformedToken(signedToken(Arrays.copyOf(validPayloadFields, 9)));
    }

    @Test
    void rejectsSignedPayloadWithElevenFields() {
        String[] elevenFields = Arrays.copyOf(validPayloadFields, 11);
        elevenFields[10] = encode("unexpected-field");

        assertMalformedToken(signedToken(elevenFields));
    }

    @Test
    void rejectsValidTokenWithTrailingDelimiter() {
        assertMalformedToken(signedToken(validPayloadFields) + ".");
    }

    @Test
    void rejectsDelimiterFreeMalformedToken() {
        assertMalformedToken(encode("malformed-token"));
    }

    @Test
    void rejectsStructurallyValidTokenWithMismatchedSignature() {
        String payload = String.join(".", validPayloadFields);
        String mismatchedSignature = hmac(payload + ".different-message");

        assertMalformedToken(payload + "." + mismatchedSignature);
    }

    @Test
    void rejectsSignedPayloadWithAnEmptyRequiredField() {
        String[] fields = validPayloadFields.clone();
        fields[1] = "";

        assertMalformedToken(signedToken(fields));
    }

    @Test
    void rejectsSignedPayloadWithMalformedBase64Url() {
        String[] fields = validPayloadFields.clone();
        fields[1] = "*";

        assertMalformedToken(signedToken(fields));
    }

    @Test
    void rejectsSignedPayloadWithNonNumericEpochSecond() {
        String[] fields = validPayloadFields.clone();
        fields[8] = encode("not-a-number");

        assertMalformedToken(signedToken(fields));
    }

    @Test
    void rejectsSignedPayloadWithOutOfRangeEpochSecond() {
        String[] fields = validPayloadFields.clone();
        fields[8] = encode(Long.toString(Long.MAX_VALUE));

        assertMalformedToken(signedToken(fields));
    }

    @Test
    void rejectsSignedPayloadWithMalformedDocumentIdentifier() {
        String[] fields = validPayloadFields.clone();
        fields[4] = encode("not-a-uuid");

        assertMalformedToken(signedToken(fields));
    }

    @Test
    void rejectsSignedPayloadWithUnsupportedVersion() {
        String[] fields = validPayloadFields.clone();
        fields[0] = encode("v0");

        assertMalformedToken(signedToken(fields));
    }

    private void assertMalformedToken(String token) {
        ArtifactTokenException exception = assertThrows(
                ArtifactTokenException.class,
                () -> service.verifyReadToken(documentId, conversionJob, artifactBytes, token)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    private static String signedToken(String[] payloadFields) {
        String payload = String.join(".", payloadFields);
        return payload + "." + hmac(payload);
    }

    private static String tokenFrom(ArtifactLinkResponse response) {
        String parameterPrefix = ArtifactLinkService.ARTIFACT_TOKEN_PARAM + "=";
        return response.artifactUrl().substring(response.artifactUrl().indexOf(parameterPrefix) + parameterPrefix.length());
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("test HMAC unavailable", exception);
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

    private static ConversionJob succeededJob(UUID documentId) {
        ConversionJob job = new ConversionJob(
                documentId,
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                "report.docx",
                "application/octet-stream",
                "hash",
                4L,
                1
        );
        job.markSucceeded("/artifacts/" + documentId + ".pdf", "done");
        return job;
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private byte nextByte = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = nextByte++;
            }
        }
    }
}
