package com.clearfolio.viewer.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.security.CredentialRegistryPort;

/**
 * Enforces request tenant claims and endpoint permissions.
 */
@Service
public class TenantAccessService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final String claimsHmacSecret;
    private final long maxSkewSeconds;
    private final Clock clock;
    private final boolean allowUnsignedClaims;

    /**
     * Creates the explicitly unsigned service used by manually bound controller
     * and unit tests. Spring runtime uses the {@link Autowired} constructor and
     * therefore never selects this compatibility boundary.
     */
    public TenantAccessService() {
        this(null, 300L, Clock.systemUTC(), true);
    }

    /**
     * Creates the Spring runtime service from credential-registry key material.
     * Missing claim-verification credentials fail closed rather than trusting
     * caller-supplied tenant headers.
     *
     * @param credentialRegistry runtime credential registry
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     */
    @Autowired
    public TenantAccessService(
            final CredentialRegistryPort credentialRegistry,
            @Value("${clearfolio.tenant-claims.max-skew-seconds:300}") final long maxSkewSeconds) {
        this(
                credentialRegistry
                        .getCredential(CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET)
                        .orElse(null),
                maxSkewSeconds,
                Clock.systemUTC(),
                false
        );
    }

    TenantAccessService(String claimsHmacSecret, long maxSkewSeconds) {
        this(claimsHmacSecret, maxSkewSeconds, Clock.systemUTC(), false);
    }

    TenantAccessService(String claimsHmacSecret, long maxSkewSeconds, Clock clock) {
        this(claimsHmacSecret, maxSkewSeconds, clock, false);
    }

    private TenantAccessService(
            String claimsHmacSecret,
            long maxSkewSeconds,
            Clock clock,
            boolean allowUnsignedClaims) {
        this.claimsHmacSecret = clean(claimsHmacSecret);
        this.maxSkewSeconds = Math.max(0L, maxSkewSeconds);
        this.clock = clock;
        this.allowUnsignedClaims = allowUnsignedClaims;
    }

    /**
     * Resolves tenant claims and verifies the required permission.
     *
     * @param headers request headers
     * @param permission required permission
     * @return verified tenant context
     */
    public TenantContext require(HttpHeaders headers, String permission) {
        TenantContext context = TenantContext.fromHeaders(headers)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "auth token required"
                ));

        requireSignedClaimsWhenConfigured(headers, context);

        if (!context.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "missing permission: " + permission);
        }

        return context;
    }

    /**
     * Hides resources that do not belong to the request tenant.
     *
     * @param context verified tenant context
     * @param job conversion job being accessed
     */
    public void requireSameTenant(TenantContext context, ConversionJob job) {
        if (context == null || job == null || !job.belongsToTenant(context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
    }

    private void requireSignedClaimsWhenConfigured(HttpHeaders headers, TenantContext context) {
        if (claimsHmacSecret == null) {
            if (allowUnsignedClaims) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "tenant claims verifier unavailable"
            );
        }

        String issuedAt = clean(headers.getFirst(TenantContext.CLAIMS_ISSUED_AT_HEADER));
        String suppliedSignature = clean(headers.getFirst(TenantContext.CLAIMS_SIGNATURE_HEADER));
        if (issuedAt == null || suppliedSignature == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "signed auth claims required");
        }

        long issuedAtEpoch = parseIssuedAt(issuedAt);
        long now = Instant.now(clock).getEpochSecond();
        if (issuedAtEpoch < now - maxSkewSeconds || issuedAtEpoch > now + maxSkewSeconds) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth claims expired");
        }

        String expectedSignature = signClaims(context, issuedAt, claimsHmacSecret);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                suppliedSignature.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth claims signature invalid");
        }
    }

    private static long parseIssuedAt(String issuedAt) {
        try {
            return Long.parseLong(issuedAt);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth claims timestamp invalid");
        }
    }

    /**
     * Signs tenant claims for gateway and test clients.
     *
     * @param context tenant context
     * @param issuedAt epoch-second issue time
     * @param secret shared gateway secret
     * @return Base64URL HMAC signature
     */
    public static String signClaims(TenantContext context, String issuedAt, String secret) {
        String payload = String.join("\n",
                context.tenantId(),
                context.subjectId(),
                context.canonicalPermissions(),
                issuedAt
        );
        return hmac(payload, secret);
    }

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return URL_ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("tenant claims signing failed", ex);
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\u0000", "").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
