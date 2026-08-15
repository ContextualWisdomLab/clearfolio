package com.clearfolio.viewer.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialReference;
import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.CredentialSnapshot;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Enforces request tenant claims and endpoint permissions.
 */
@Service
public class TenantAccessService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final CredentialReference TENANT_CLAIMS_CREDENTIAL = new CredentialReference(
            "tenant-claims-signing",
            CredentialPurpose.TENANT_CLAIMS_SIGNING
    );

    private final byte[] claimsHmacSecret;
    private final long maxSkewSeconds;
    private final Clock clock;

    /**
     * Creates an access service for local tests and unsigned demo mode.
     */
    public TenantAccessService() {
        this("", 300L, Clock.systemUTC());
    }

    /**
     * Creates an access service with optional signed gateway claim validation.
     *
     * <p>This compatibility constructor remains the current Spring wiring until
     * the credential-registry bootstrap stack is integrated. Production
     * application wiring must migrate to the registry-backed constructor rather
     * than treating this property as long-lived secret authority.</p>
     *
     * @param claimsHmacSecret optional shared gateway HMAC secret
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     */
    @Autowired
    public TenantAccessService(
            @Value("${clearfolio.tenant-claims.hmac-secret:}") String claimsHmacSecret,
            @Value("${clearfolio.tenant-claims.max-skew-seconds:300}") long maxSkewSeconds) {
        this(claimsHmacSecret, maxSkewSeconds, Clock.systemUTC());
    }

    /**
     * Creates a verifier from the provider-neutral credential registry.
     *
     * <p>The server-owned reference is fixed to tenant-claim signing. The
     * returned snapshot must bind both the expected logical credential and
     * purpose before its opaque bytes become verification authority.</p>
     *
     * @param credentialRegistry provider-neutral credential authority
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     * @param clock verification clock
     * @throws NullPointerException when registry, clock, or snapshot is absent
     * @throws IllegalStateException when registry metadata does not match the
     *         server-owned tenant-claims reference
     */
    public TenantAccessService(
            CredentialRegistry credentialRegistry,
            long maxSkewSeconds,
            Clock clock
    ) {
        CredentialSnapshot snapshot = Objects.requireNonNull(
                Objects.requireNonNull(credentialRegistry, "credentialRegistry")
                        .resolve(TENANT_CLAIMS_CREDENTIAL),
                "tenant claims credential snapshot"
        );
        if (!TENANT_CLAIMS_CREDENTIAL.credentialName().equals(snapshot.credentialId())
                || snapshot.purpose() != TENANT_CLAIMS_CREDENTIAL.purpose()) {
            throw new IllegalStateException("tenant claims credential purpose mismatch");
        }
        this.claimsHmacSecret = snapshot.secretBytes();
        this.maxSkewSeconds = Math.max(0L, maxSkewSeconds);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    TenantAccessService(String claimsHmacSecret, long maxSkewSeconds, Clock clock) {
        String cleanedSecret = clean(claimsHmacSecret);
        this.claimsHmacSecret = cleanedSecret == null
                ? null
                : cleanedSecret.getBytes(StandardCharsets.UTF_8);
        this.maxSkewSeconds = Math.max(0L, maxSkewSeconds);
        this.clock = Objects.requireNonNull(clock, "clock");
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
            return;
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
        return signClaims(
                context,
                issuedAt,
                Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String signClaims(TenantContext context, String issuedAt, byte[] secret) {
        String payload = String.join("\n",
                context.tenantId(),
                context.subjectId(),
                context.canonicalPermissions(),
                issuedAt
        );
        return hmac(payload, secret);
    }

    private static String hmac(String payload, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
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
