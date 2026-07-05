package com.clearfolio.viewer.artifact;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkRevocationRequest;
import com.clearfolio.viewer.api.ArtifactLinkRevocationResponse;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ArtifactReadEventResponse;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * Issues and verifies tenant-bound HMAC artifact tokens.
 */
@Service
public class ArtifactLinkService {

    /**
     * Query parameter used by PDF.js-compatible artifact URLs.
     */
    public static final String ARTIFACT_TOKEN_PARAM = "artifactToken";

    /**
     * Scope required to read artifact bytes.
     */
    public static final String ARTIFACT_READ_SCOPE = "artifact:read";

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final String DEFAULT_PURPOSE = "viewer-preview";
    private static final String DEFAULT_REVOKE_REASON = "operator-request";
    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int MAX_TTL_SECONDS = 900;
    private static final int TOKEN_FIELD_COUNT = 10;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    // ⚡ Bolt: HexFormat 인스턴스 재사용을 통한 메모리 할당 최소화
    private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();

    private final ArtifactStore artifactStore;
    private final ArtifactLinkLedger artifactLinkLedger;
    private final SecretKeySpec signingKey;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /**
     * Creates the link service with an optional configured HMAC secret.
     *
     * @param artifactStore artifact byte store
     * @param configuredSecret optional deployment secret
     */
    @Autowired
    public ArtifactLinkService(
            ArtifactStore artifactStore,
            ArtifactLinkLedger artifactLinkLedger,
            @Value("${clearfolio.artifact-token.secret:}") String configuredSecret) {
        this(artifactStore, artifactLinkLedger, configuredSecret, Clock.systemUTC(), new SecureRandom());
    }

    /**
     * Creates the link service with an isolated runtime ledger.
     *
     * @param artifactStore artifact byte store
     * @param configuredSecret optional deployment secret
     */
    public ArtifactLinkService(
            ArtifactStore artifactStore,
            String configuredSecret) {
        this(artifactStore, new ArtifactLinkLedger(), configuredSecret, Clock.systemUTC(), new SecureRandom());
    }

    ArtifactLinkService(
            ArtifactStore artifactStore,
            String configuredSecret,
            Clock clock,
            SecureRandom secureRandom) {
        this(artifactStore, new ArtifactLinkLedger(), configuredSecret, clock, secureRandom);
    }

    ArtifactLinkService(
            ArtifactStore artifactStore,
            ArtifactLinkLedger artifactLinkLedger,
            String configuredSecret,
            Clock clock,
            SecureRandom secureRandom) {
        this.artifactStore = artifactStore;
        this.artifactLinkLedger = artifactLinkLedger;
        this.signingKey = new SecretKeySpec(secretBytes(configuredSecret, secureRandom), HMAC_SHA_256);
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Creates a signed artifact link for a succeeded same-tenant conversion job.
     *
     * @param job succeeded conversion job
     * @param tenantContext verified tenant context
     * @param request link request
     * @return signed artifact link response
     */
    public ArtifactLinkResponse createLink(
            ConversionJob job,
            TenantContext tenantContext,
            ArtifactLinkRequest request) {
        if (job == null || tenantContext == null || !job.belongsToTenant(tenantContext.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found");
        }
        if (job.getStatus() != ConversionJobStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found");
        }

        byte[] artifactBytes = artifactStore.getPdf(job.getJobId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
        ArtifactLinkRequest effectiveRequest = request == null ? ArtifactLinkRequest.viewerPreview() : request;
        int ttlSeconds = ttlSecondsOf(effectiveRequest.ttlSeconds());
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        String tokenId = randomTokenId();
        String purpose = purposeOf(effectiveRequest.purpose());
        String checksum = sha256Hex(artifactBytes);
        ArtifactTokenClaims claims = new ArtifactTokenClaims(
                tokenId,
                job.getTenantId(),
                tenantContext.subjectId(),
                job.getJobId(),
                ARTIFACT_READ_SCOPE,
                purpose,
                checksum,
                issuedAt,
                expiresAt
        );
        String token = sign(claims);
        artifactLinkLedger.recordIssued(new ArtifactLinkRecord(
                tokenId,
                claims.tenantId(),
                claims.subjectId(),
                claims.docId(),
                claims.scope(),
                claims.purpose(),
                claims.artifactChecksum(),
                nullableClean(effectiveRequest.viewerSessionId()),
                claims.issuedAt(),
                claims.expiresAt(),
                null,
                null,
                null
        ));
        String artifactUrl = "/artifacts/" + job.getJobId() + ".pdf?"
                + ARTIFACT_TOKEN_PARAM + "=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new ArtifactLinkResponse(
                artifactUrl,
                expiresAt,
                tokenId,
                ARTIFACT_READ_SCOPE,
                job.getJobId().toString()
        );
    }

    /**
     * Verifies a token before serving artifact bytes.
     *
     * @param docId route document identifier
     * @param job conversion job
     * @param artifactBytes stored artifact bytes
     * @param token supplied artifact token
     * @return verified artifact token claims
     */
    public ArtifactTokenClaims verifyReadToken(UUID docId, ConversionJob job, byte[] artifactBytes, String token) {
        if (token == null || token.isBlank()) {
            throw new ArtifactTokenException(HttpStatus.UNAUTHORIZED, "artifact token required");
        }

        ArtifactTokenClaims claims = parseAndVerify(token);
        if (claims.expiresAt().compareTo(Instant.now(clock)) <= 0) {
            throw new ArtifactTokenException(HttpStatus.UNAUTHORIZED, "artifact token expired");
        }
        if (!ARTIFACT_READ_SCOPE.equals(claims.scope())) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token scope denied");
        }
        if (!docId.equals(claims.docId())) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token doc mismatch");
        }
        ArtifactLinkRecord record = artifactLinkLedger.findByTokenId(claims.tokenId())
                .orElseThrow(() -> new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token unknown"));
        if (record.isRevoked()) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token revoked");
        }
        if (!record.tenantId().equals(claims.tenantId())
                || !record.docId().equals(claims.docId())
                || !record.artifactChecksum().equals(claims.artifactChecksum())) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token ledger mismatch");
        }
        if (job == null || !job.belongsToTenant(claims.tenantId())) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token tenant mismatch");
        }
        if (!sha256Hex(artifactBytes).equals(claims.artifactChecksum())) {
            throw new ArtifactTokenException(HttpStatus.FORBIDDEN, "artifact token checksum mismatch");
        }
        return claims;
    }

    /**
     * Revokes a previously issued artifact link for the current tenant.
     *
     * @param tokenId artifact token identifier
     * @param tenantContext verified tenant context
     * @param request optional revocation request
     * @return revocation response
     */
    public ArtifactLinkRevocationResponse revokeLink(
            String tokenId,
            TenantContext tenantContext,
            ArtifactLinkRevocationRequest request) {
        String effectiveTokenId = nullableClean(tokenId);
        if (effectiveTokenId == null || tenantContext == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact link not found");
        }

        ArtifactLinkRecord record = artifactLinkLedger.findByTokenId(effectiveTokenId)
                .filter(link -> link.tenantId().equals(tenantContext.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact link not found"));
        ArtifactLinkRecord revoked = artifactLinkLedger.revoke(
                record.tokenId(),
                Instant.now(clock),
                tenantContext.subjectId(),
                reasonOf(request == null ? null : request.reason())
        ).orElse(record);

        return new ArtifactLinkRevocationResponse(
                revoked.tokenId(),
                revoked.revokedAt(),
                revoked.revokedBy(),
                revoked.revokeReason(),
                revoked.isRevoked()
        );
    }

    /**
     * Records an audited artifact read after token verification.
     *
     * @param claims verified token claims
     * @param rangeRequested optional HTTP range header
     * @param statusCode response status code
     * @param traceId optional caller supplied trace identifier
     */
    public void recordRead(
            ArtifactTokenClaims claims,
            String rangeRequested,
            int statusCode,
            String traceId) {
        artifactLinkLedger.recordRead(new ArtifactReadEvent(
                claims.tenantId(),
                claims.subjectId(),
                claims.docId(),
                claims.tokenId(),
                nullableClean(rangeRequested),
                statusCode,
                nullableClean(traceId),
                Instant.now(clock)
        ));
    }

    /**
     * Returns audited artifact reads for the current tenant and document.
     *
     * @param docId document identifier
     * @param tenantContext verified tenant context
     * @return matching audit events
     */
    public List<ArtifactReadEventResponse> readEvents(UUID docId, TenantContext tenantContext) {
        return artifactLinkLedger.readEventsFor(tenantContext.tenantId(), docId).stream()
                .map(event -> new ArtifactReadEventResponse(
                        event.tenantId(),
                        event.subjectId(),
                        event.docId().toString(),
                        event.tokenId(),
                        event.rangeRequested(),
                        event.statusCode(),
                        event.traceId(),
                        event.readAt()
                ))
                .toList();
    }

    /**
     * Resolves a token from query parameter or bearer authorization header.
     *
     * @param queryToken token from query parameter
     * @param authorizationHeader optional authorization header
     * @return token when present
     */
    public static String resolveToken(String queryToken, String authorizationHeader) {
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.strip();
        }
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix)) {
            return null;
        }

        String bearerToken = authorizationHeader.substring(prefix.length()).strip();
        return bearerToken.isEmpty() ? null : bearerToken;
    }

    private ArtifactTokenClaims parseAndVerify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != TOKEN_FIELD_COUNT + 1) {
            throw new ArtifactTokenException(HttpStatus.UNAUTHORIZED, "artifact token invalid");
        }

        String payload = String.join(".", Arrays.copyOf(parts, TOKEN_FIELD_COUNT));
        String expectedSignature = hmac(payload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[TOKEN_FIELD_COUNT].getBytes(StandardCharsets.US_ASCII))) {
            throw new ArtifactTokenException(HttpStatus.UNAUTHORIZED, "artifact token invalid");
        }

        try {
            String version = decode(parts[0]);
            if (!VERSION.equals(version)) {
                throw new IllegalArgumentException("unsupported artifact token version");
            }
            return new ArtifactTokenClaims(
                    decode(parts[1]),
                    decode(parts[2]),
                    decode(parts[3]),
                    UUID.fromString(decode(parts[4])),
                    decode(parts[5]),
                    decode(parts[6]),
                    decode(parts[7]),
                    Instant.ofEpochSecond(Long.parseLong(decode(parts[8]))),
                    Instant.ofEpochSecond(Long.parseLong(decode(parts[9])))
            );
        } catch (IllegalArgumentException ex) {
            throw new ArtifactTokenException(HttpStatus.UNAUTHORIZED, "artifact token invalid");
        }
    }

    private String sign(ArtifactTokenClaims claims) {
        String payload = String.join(".",
                encode(VERSION),
                encode(claims.tokenId()),
                encode(claims.tenantId()),
                encode(claims.subjectId()),
                encode(claims.docId().toString()),
                encode(claims.scope()),
                encode(claims.purpose()),
                encode(claims.artifactChecksum()),
                encode(String.valueOf(claims.issuedAt().getEpochSecond())),
                encode(String.valueOf(claims.expiresAt().getEpochSecond()))
        );
        return payload + "." + hmac(payload);
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(signingKey);
            return URL_ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("artifact token signing failed", ex);
        }
    }

    private String randomTokenId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static int ttlSecondsOf(Integer requestedTtlSeconds) {
        if (requestedTtlSeconds == null || requestedTtlSeconds <= 0) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.min(requestedTtlSeconds, MAX_TTL_SECONDS);
    }

    private static String purposeOf(String requestedPurpose) {
        return Optional.ofNullable(nullableClean(requestedPurpose))
                .orElse(DEFAULT_PURPOSE);
    }

    private static String reasonOf(String requestedReason) {
        return Optional.ofNullable(nullableClean(requestedReason))
                .orElse(DEFAULT_REVOKE_REASON);
    }

    private static String nullableClean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\u0000", "").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(bytes);
            // ⚡ Bolt: 반복적인 String.format 루프를 제거하여 가비지 컬렉션 부하를 획기적으로 개선
            return HEX_FORMAT.formatHex(raw);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private static String encode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(URL_DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static byte[] secretBytes(String configuredSecret, SecureRandom secureRandom) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        byte[] generated = new byte[32];
        secureRandom.nextBytes(generated);
        return generated;
    }
}
