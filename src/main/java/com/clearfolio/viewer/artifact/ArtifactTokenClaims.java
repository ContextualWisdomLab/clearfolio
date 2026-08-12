package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Verified artifact token claims.
 *
 * @param tokenId artifact token identifier
 * @param tenantId tenant that owns the artifact
 * @param subjectId subject encoded in the token
 * @param docId document identifier bound to the token
 * @param scope token scope
 * @param purpose caller-visible token purpose
 * @param artifactChecksum artifact checksum bound to the token
 * @param issuedAt token issue timestamp
 * @param expiresAt token expiration timestamp
 */
public record ArtifactTokenClaims(
        String tokenId,
        String tenantId,
        String subjectId,
        UUID docId,
        String scope,
        String purpose,
        String artifactChecksum,
        Instant issuedAt,
        Instant expiresAt
) {

    /**
     * Creates claims only when every signed field has a usable value. Text is
     * validated without trimming or normalization so the verified HMAC remains
     * bound to the exact payload supplied by the issuer.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if any text component is blank
     */
    public ArtifactTokenClaims {
        tokenId = requireText(tokenId, "tokenId");
        tenantId = requireText(tenantId, "tenantId");
        subjectId = requireText(subjectId, "subjectId");
        docId = Objects.requireNonNull(docId, "docId");
        scope = requireText(scope, "scope");
        purpose = requireText(purpose, "purpose");
        artifactChecksum = requireText(artifactChecksum, "artifactChecksum");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
