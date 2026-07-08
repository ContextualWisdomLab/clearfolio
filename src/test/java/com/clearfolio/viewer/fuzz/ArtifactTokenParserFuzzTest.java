package com.clearfolio.viewer.fuzz;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactTokenException;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;

/**
 * Fuzzes {@link ArtifactLinkService}'s artifact-token verification path.
 *
 * <p>The token string is fully attacker-controlled (query parameter or bearer
 * header). {@link ArtifactLinkService#verifyReadToken} splits it, HMAC-verifies
 * it, then Base64URL-decodes and parses ten fields (including a {@link UUID},
 * two {@code Long}s and two {@link java.time.Instant}s). The invariant under
 * test: for <em>any</em> input the method must fail with the controlled
 * {@link ArtifactTokenException} (mapped to a 4xx) and never leak an
 * unhandled runtime exception (which would surface as a 500).
 *
 * <p>To exercise the deep decode/parse path -- not just the signature gate --
 * the harness forges structurally valid, correctly-signed tokens using a known
 * secret, mirroring {@code ArtifactLinkService.sign}. This is what surfaced the
 * previously uncaught {@link java.time.DateTimeException} thrown by
 * {@code Instant.ofEpochSecond} on out-of-range epoch seconds.
 */
final class ArtifactTokenParserFuzzTest {

    private static final String SECRET = "clearfolio-fuzz-artifact-secret";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ArtifactLinkService service =
            new ArtifactLinkService(new InMemoryArtifactStore(), SECRET);

    @FuzzTest(maxDuration = "60s")
    void verifyReadTokenNeverThrowsUnhandled(FuzzedDataProvider data) {
        boolean forgeSignature = data.consumeBoolean();
        UUID routeDocId = new UUID(data.consumeLong(), data.consumeLong());

        String token;
        if (forgeSignature) {
            // Build a token with a valid HMAC so parsing runs to completion.
            String version = data.consumeBoolean() ? "v1" : data.consumeString(8);
            UUID docId = new UUID(data.consumeLong(), data.consumeLong());
            String[] fields = {
                    version,
                    data.consumeString(24),                       // tokenId
                    data.consumeString(24),                       // tenantId
                    data.consumeString(24),                       // subjectId
                    data.consumeBoolean() ? docId.toString()
                            : data.consumeString(40),             // docId (valid UUID or garbage)
                    data.consumeString(24),                       // scope
                    data.consumeString(24),                       // purpose
                    data.consumeString(64),                       // artifactChecksum
                    Long.toString(data.consumeLong()),            // issuedAt epoch seconds
                    Long.toString(data.consumeLong()),            // expiresAt epoch seconds
            };
            token = sign(fields);
        } else {
            // Fully arbitrary token: exercises the split + signature-gate path.
            token = data.consumeRemainingAsString();
        }

        try {
            service.verifyReadToken(routeDocId, null, new byte[0], token);
        } catch (ArtifactTokenException expected) {
            // Controlled failure -> 4xx. Correct behavior for hostile input.
        }
    }

    private static String sign(String[] fields) {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                payload.append('.');
            }
            payload.append(ENCODER.encodeToString(fields[i].getBytes(StandardCharsets.UTF_8)));
        }
        String signature = hmac(payload.toString());
        return payload + "." + signature;
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("test HMAC failed", ex);
        }
    }
}
