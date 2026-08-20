package com.clearfolio.viewer.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import com.clearfolio.viewer.credential.CredentialPurpose;
import com.clearfolio.viewer.credential.CredentialReference;
import com.clearfolio.viewer.credential.CredentialRegistry;
import com.clearfolio.viewer.credential.CredentialSnapshot;

/**
 * Fails closed when production starts without stable, adequately sized, purpose-separated HMAC
 * signing material.
 */
@Configuration
@Profile("production")
public class ProductionAuthReadinessConfig {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;
    private static final CredentialReference TENANT_CLAIMS_CREDENTIAL = new CredentialReference(
            "tenant-claims-signing",
            CredentialPurpose.TENANT_CLAIMS_SIGNING
    );

    /**
     * Verifies the production tenant-claims registry authority and artifact-token ConfigTree key.
     *
     * <p>The tenant verifier resolves one fixed provider-neutral credential reference. Readiness
     * requires the exact logical identifier, tenant-claim purpose, and at least 32 opaque bytes.
     * The artifact-token service remains on its current flat ConfigTree property during this
     * bounded migration and therefore still requires a stable explicit key of at least 32 UTF-8
     * bytes. The two effective keys must be byte-distinct.</p>
     *
     * <p>Resolved copies are cleared after validation. Key length and purpose separation do not
     * prove entropy, independent generation, KMS/HSM custody, active/previous rotation, or
     * compromise recovery; those remain explicit issue-#319 follow-on controls.</p>
     *
     * @param credentialRegistry provider-neutral tenant-claims credential authority
     * @param artifactTokenSecret stable artifact-token signing secret loaded through ConfigTree
     * @throws NullPointerException when the registry is absent
     * @throws IllegalStateException when either authority is missing, weak, mismatched, or reused
     */
    public ProductionAuthReadinessConfig(
            CredentialRegistry credentialRegistry,
            @Value("${clearfolio.artifact-token.secret:}") String artifactTokenSecret) {
        CredentialSnapshot tenantSnapshot = Objects.requireNonNull(
                credentialRegistry,
                "credentialRegistry"
        ).resolveScoped(TENANT_CLAIMS_CREDENTIAL);
        if (!TENANT_CLAIMS_CREDENTIAL.credentialName().equals(tenantSnapshot.credentialId())) {
            throw new IllegalStateException("production tenant-claims credential identity mismatch");
        }

        byte[] tenantClaimsKey = tenantSnapshot.secretBytes();
        try {
            if (tenantClaimsKey.length < MINIMUM_HMAC_KEY_BYTES) {
                throw new IllegalStateException(
                        "production tenant-claims credential requires at least 32 bytes"
                );
            }
            if (!StringUtils.hasText(artifactTokenSecret)) {
                throw new IllegalStateException(
                        "production profile requires clearfolio.artifact-token.secret"
                );
            }

            byte[] artifactTokenKey = artifactTokenSecret.getBytes(StandardCharsets.UTF_8);
            try {
                if (artifactTokenKey.length < MINIMUM_HMAC_KEY_BYTES) {
                    throw new IllegalStateException(
                            "production profile requires clearfolio.artifact-token.secret with at least 32 UTF-8 bytes"
                    );
                }
                if (MessageDigest.isEqual(tenantClaimsKey, artifactTokenKey)) {
                    throw new IllegalStateException(
                            "production profile requires distinct tenant-claims and artifact-token HMAC secrets"
                    );
                }
            } finally {
                Arrays.fill(artifactTokenKey, (byte) 0);
            }
        } finally {
            Arrays.fill(tenantClaimsKey, (byte) 0);
        }
    }
}
