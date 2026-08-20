package com.clearfolio.viewer.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
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
    private static final int MINIMUM_HMAC_KEY_BYTES = 32;
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
        this((CredentialRegistry) null, "", 300L, Clock.systemUTC());
    }

    /**
     * Creates the Spring-managed access service.
     *
     * <p>When a provider-neutral registry bean exists, its tenant-claim signing
     * credential is the only verification authority. Development and explicit
     * demo compositions without a registry retain the bounded legacy property
     * path so local unsigned and signed fixtures remain usable.</p>
     *
     * @param credentialRegistryProvider optional provider-neutral registry bean
     * @param legacyClaimsHmacSecret development/demo compatibility property
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     * @throws NullPointerException when the registry provider is absent
     */
    @Autowired
    public TenantAccessService(
            ObjectProvider<CredentialRegistry> credentialRegistryProvider,
            @Value("${clearfolio.tenant-claims.hmac-secret:}") String legacyClaimsHmacSecret,
            @Value("${clearfolio.tenant-claims.max-skew-seconds:300}") long maxSkewSeconds) {
        this(
                Objects.requireNonNull(
                        credentialRegistryProvider,
                        "credentialRegistryProvider"
                ).getIfAvailable(),
                legacyClaimsHmacSecret,
                maxSkewSeconds,
                Clock.systemUTC()
        );
    }

    /**
     * Creates an access service with optional signed gateway claim validation.
     *
     * @param claimsHmacSecret optional development/demo HMAC secret
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     */
    public TenantAccessService(String claimsHmacSecret, long maxSkewSeconds) {
        this((CredentialRegistry) null, claimsHmacSecret, maxSkewSeconds, Clock.systemUTC());
    }

    /**
     * Creates a verifier from the provider-neutral credential registry.
     *
     * <p>The server-owned reference is fixed to tenant-claim signing. Scoped
     * resolution enforces purpose separation, while this consumer additionally
     * requires the exact logical credential identity and the HMAC-SHA-256
     * minimum key size before resolved bytes become verification authority.</p>
     *
     * @param credentialRegistry provider-neutral credential authority
     * @param maxSkewSeconds maximum accepted clock skew in seconds
     * @param clock verification clock
     * @throws NullPointerException when registry or clock is absent
     * @throws IllegalStateException when registry identity or key size is invalid
     */
    public TenantAccessService(
            CredentialRegistry credentialRegistry,
            long maxSkewSeconds,
            Clock clock
    ) {
        this(
                Objects.requireNonNull(credentialRegistry, "credentialRegistry"),
                null,
                maxSkewSeconds,
                clock
        );
    }

    TenantAccessService(String claimsHmacSecret, long maxSkewSeconds, Clock clock) {
        this((CredentialRegistry) null, claimsHmacSecret, maxSkewSeconds, clock);
    }

    private TenantAccessService(
            CredentialRegistry credentialRegistry,
            String legacyClaimsHmacSecret,
            long maxSkewSeconds,
            Clock clock
    ) {
        this.maxSkewSeconds = Math.max(0L, maxSkewSeconds);
        this.clock = Objects.requireNonNull(clock, "clock");
        if (credentialRegistry == null) {
            String cleanedSecret = clean(legacyClaimsHmacSecret);
            this.claimsHmacSecret = cleanedSecret == null
                    ? null
                    : cleanedSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            this.claimsHmacSecret = registrySecretBytes(credentialRegistry);
        }
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

    private static byte[] registrySecretBytes(CredentialRegistry credentialRegistry) {
        CredentialSnapshot snapshot = credentialRegistry.resolveScoped(TENANT_CLAIMS_CREDENTIAL);
        if (!TENANT_CLAIMS_CREDENTIAL.credentialName().equals(snapshot.credentialId())) {
            throw new IllegalStateException("tenant claims credential identity mismatch");
        }

        byte[] resolvedSecret = snapshot.secretBytes();
        try {
            if (resolvedSecret.length < MINIMUM_HMAC_KEY_BYTES) {
                throw new IllegalStateException(
                        "tenant claims credential requires at least 32 bytes"
                );
            }
            return resolvedSecret.clone();
        } finally {
            Arrays.fill(resolvedSecret, (byte) 0);
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
