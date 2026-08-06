package com.clearfolio.viewer.lifecycle;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable evidence for one tenant-bound artifact-deletion lifecycle.
 *
 * @param requestId idempotency identifier supplied for the deletion request
 * @param tenantId tenant that owned the deleted conversion job
 * @param jobId permanently reserved conversion-job identifier
 * @param artifactChecksum lowercase SHA-256 digest binding cleanup to one artifact generation
 * @param auditCorrelationId privacy-safe identifier joining lifecycle audit evidence
 * @param requestedAt instant when the deletion request became durable
 * @param stateChangedAt instant when the current state became durable
 * @param state current monotonic lifecycle state
 * @param attemptCount number of failed cleanup attempts
 * @param lastAttemptAt instant of the latest failed cleanup attempt, when any
 * @param completedAt instant when cleanup became terminally complete, when any
 * @param failureCode controlled non-sensitive failure code, when cleanup failed
 */
public record ArtifactDeletionReceipt(
        UUID requestId,
        String tenantId,
        UUID jobId,
        String artifactChecksum,
        String auditCorrelationId,
        Instant requestedAt,
        Instant stateChangedAt,
        ArtifactDeletionState state,
        int attemptCount,
        Instant lastAttemptAt,
        Instant completedAt,
        String failureCode
) {

    private static final int MAX_IDENTIFIER_LENGTH = 256;
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FAILURE_CODE_PATTERN = Pattern.compile("[a-z0-9_]{1,64}");

    /**
     * Validates one immutable receipt snapshot.
     */
    public ArtifactDeletionReceipt {
        requestId = Objects.requireNonNull(requestId, "requestId");
        tenantId = requireText(tenantId, "tenantId");
        jobId = Objects.requireNonNull(jobId, "jobId");
        artifactChecksum = requireChecksum(artifactChecksum);
        auditCorrelationId = requireText(auditCorrelationId, "auditCorrelationId");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        stateChangedAt = Objects.requireNonNull(stateChangedAt, "stateChangedAt");
        state = Objects.requireNonNull(state, "state");
        if (stateChangedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("stateChangedAt must not precede requestedAt");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        failureCode = normalizeOptional(failureCode);
        validateStateFields(
                requestedAt,
                stateChangedAt,
                state,
                attemptCount,
                lastAttemptAt,
                completedAt,
                failureCode
        );
    }

    /**
     * Returns whether artifact cleanup completed successfully.
     *
     * @return true only for the terminal completed state
     */
    public boolean isCompleted() {
        return state == ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED;
    }

    /**
     * Returns whether this receipt represents the same immutable request.
     *
     * @param other candidate receipt identity
     * @return true when all immutable identity fields match
     */
    public boolean hasSameIdentity(ArtifactDeletionReceipt other) {
        return other != null
                && requestId.equals(other.requestId)
                && tenantId.equals(other.tenantId)
                && jobId.equals(other.jobId)
                && artifactChecksum.equals(other.artifactChecksum)
                && auditCorrelationId.equals(other.auditCorrelationId)
                && requestedAt.equals(other.requestedAt);
    }

    ArtifactDeletionReceipt markMetadataTombstoned(Instant transitionedAt) {
        requireState(ArtifactDeletionState.DELETION_REQUESTED);
        return snapshot(
                ArtifactDeletionState.METADATA_TOMBSTONED,
                requireForwardTime(transitionedAt),
                attemptCount,
                lastAttemptAt,
                null,
                null
        );
    }

    ArtifactDeletionReceipt markCleanupPending(Instant transitionedAt) {
        if (state != ArtifactDeletionState.METADATA_TOMBSTONED
                && state != ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED) {
            throw invalidTransition();
        }
        return snapshot(
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                requireForwardTime(transitionedAt),
                attemptCount,
                lastAttemptAt,
                null,
                null
        );
    }

    ArtifactDeletionReceipt recordCleanupFailure(String controlledFailureCode, Instant attemptedAt) {
        requireState(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING);
        Instant requiredAttemptedAt = requireForwardTime(attemptedAt);
        return snapshot(
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                requiredAttemptedAt,
                Math.addExact(attemptCount, 1),
                requiredAttemptedAt,
                null,
                requireFailureCode(controlledFailureCode)
        );
    }

    ArtifactDeletionReceipt markCleanupCompleted(Instant completionInstant) {
        requireState(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING);
        Instant requiredCompletionInstant = requireForwardTime(completionInstant);
        return snapshot(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                requiredCompletionInstant,
                attemptCount,
                lastAttemptAt,
                requiredCompletionInstant,
                null
        );
    }

    private ArtifactDeletionReceipt snapshot(
            ArtifactDeletionState nextState,
            Instant nextStateChangedAt,
            int nextAttemptCount,
            Instant nextLastAttemptAt,
            Instant nextCompletedAt,
            String nextFailureCode
    ) {
        return new ArtifactDeletionReceipt(
                requestId,
                tenantId,
                jobId,
                artifactChecksum,
                auditCorrelationId,
                requestedAt,
                nextStateChangedAt,
                nextState,
                nextAttemptCount,
                nextLastAttemptAt,
                nextCompletedAt,
                nextFailureCode
        );
    }

    private Instant requireForwardTime(Instant candidate) {
        Instant requiredCandidate = Objects.requireNonNull(candidate, "transitionedAt");
        if (requiredCandidate.isBefore(stateChangedAt)) {
            throw new IllegalArgumentException("stateChangedAt must not precede the prior transition");
        }
        return requiredCandidate;
    }

    private void requireState(ArtifactDeletionState requiredState) {
        if (state != requiredState) {
            throw invalidTransition();
        }
    }

    private static void validateStateFields(
            Instant currentRequestedAt,
            Instant currentStateChangedAt,
            ArtifactDeletionState currentState,
            int currentAttemptCount,
            Instant currentLastAttemptAt,
            Instant currentCompletedAt,
            String currentFailureCode
    ) {
        if (currentLastAttemptAt != null && currentLastAttemptAt.isBefore(currentRequestedAt)) {
            throw new IllegalArgumentException("lastAttemptAt must not precede requestedAt");
        }
        if (currentLastAttemptAt != null && currentLastAttemptAt.isAfter(currentStateChangedAt)) {
            throw new IllegalArgumentException("lastAttemptAt must not follow stateChangedAt");
        }
        boolean hasAttempts = currentAttemptCount > 0;
        if (hasAttempts != (currentLastAttemptAt != null)) {
            throw new IllegalArgumentException("cleanup attempt evidence is inconsistent");
        }
        if (currentState == ArtifactDeletionState.DELETION_REQUESTED) {
            if (!currentStateChangedAt.equals(currentRequestedAt) || hasAttempts) {
                throw new IllegalArgumentException("requested receipt fields are inconsistent");
            }
        }
        if (currentState == ArtifactDeletionState.METADATA_TOMBSTONED && hasAttempts) {
            throw new IllegalArgumentException("tombstoned receipt fields are inconsistent");
        }
        if (currentState == ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED) {
            if (currentCompletedAt == null
                    || !currentCompletedAt.equals(currentStateChangedAt)
                    || currentFailureCode != null) {
                throw new IllegalArgumentException("completed receipt fields are inconsistent");
            }
            return;
        }
        if (currentCompletedAt != null) {
            throw new IllegalArgumentException("only completed receipts may have completedAt");
        }
        if (currentState == ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED) {
            if (!hasAttempts
                    || !currentLastAttemptAt.equals(currentStateChangedAt)
                    || currentFailureCode == null) {
                throw new IllegalArgumentException("failed receipt fields are inconsistent");
            }
        } else if (currentFailureCode != null) {
            throw new IllegalArgumentException("only failed receipts may have failureCode");
        }
    }

    private static String requireChecksum(String value) {
        String normalized = requireText(value, "artifactChecksum");
        if (!SHA_256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("artifactChecksum must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    private static String requireFailureCode(String value) {
        String normalized = requireText(value, "controlledFailureCode");
        if (!FAILURE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("failureCode must be a controlled code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds the configured bound");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static IllegalStateException invalidTransition() {
        return new IllegalStateException("artifact deletion receipt transition is invalid");
    }
}
