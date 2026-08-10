package com.clearfolio.viewer.lifecycle;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable authority binding for one durable artifact-deletion request.
 *
 * <p>The value object deliberately contains no storage path, filename, token,
 * document bytes, or raw subject identifier. Its artifact checksum is an exact
 * lowercase SHA-256 digest so later deletion-receipt work can bind cleanup to
 * one previously observed artifact generation without accepting an ambiguous
 * textual representation.</p>
 *
 * @param requestId immutable idempotency identifier for the deletion request
 * @param tenantId server-authoritative tenant that owns the conversion job
 * @param jobId permanently reserved conversion-job identifier
 * @param artifactChecksum lowercase SHA-256 digest of the bound artifact bytes
 * @param auditCorrelationId privacy-safe correlation identifier for lifecycle evidence
 * @param requestedAt instant when the deletion request identity became authoritative
 */
public record ArtifactDeletionReceiptIdentity(
        UUID requestId,
        String tenantId,
        UUID jobId,
        String artifactChecksum,
        String auditCorrelationId,
        Instant requestedAt
) {

    private static final int MAX_IDENTIFIER_LENGTH = 256;
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    /**
     * Validates and normalizes one immutable deletion-receipt identity.
     *
     * @throws NullPointerException when an immutable identifier or timestamp is absent
     * @throws IllegalArgumentException when bounded text is blank/oversized or the artifact digest is not canonical SHA-256
     */
    public ArtifactDeletionReceiptIdentity {
        requestId = Objects.requireNonNull(requestId, "requestId");
        tenantId = requireBoundedText(tenantId, "tenantId");
        jobId = Objects.requireNonNull(jobId, "jobId");
        artifactChecksum = requireSha256(artifactChecksum);
        auditCorrelationId = requireBoundedText(auditCorrelationId, "auditCorrelationId");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    }

    private static String requireSha256(String value) {
        String normalized = requireBoundedText(value, "artifactChecksum");
        if (!SHA_256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "artifactChecksum must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    private static String requireBoundedText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds the configured bound");
        }
        return normalized;
    }
}
