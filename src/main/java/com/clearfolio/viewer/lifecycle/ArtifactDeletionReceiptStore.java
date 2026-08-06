package com.clearfolio.viewer.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned persistence boundary for tenant-bound artifact-deletion receipts.
 *
 * <p>Standalone deployments may use the append-only reference ledger. Durable
 * MSA adapters must preserve the same immutable request identity, monotonic
 * state transitions, exact artifact digest, privacy-safe audit correlation,
 * idempotency, and fail-closed conflict semantics.</p>
 */
public interface ArtifactDeletionReceiptStore {

    /**
     * Durably accepts one idempotent artifact-deletion request.
     *
     * @param requestId deletion idempotency identifier
     * @param tenantId tenant that owns the conversion job
     * @param jobId permanently reserved conversion-job identifier
     * @param artifactChecksum lowercase SHA-256 artifact digest
     * @param auditCorrelationId privacy-safe audit correlation identifier
     * @param requestedAt instant when the request became durable
     * @return new or previously accepted identical receipt
     */
    ArtifactDeletionReceipt request(
            UUID requestId,
            String tenantId,
            UUID jobId,
            String artifactChecksum,
            String auditCorrelationId,
            Instant requestedAt
    );

    /**
     * Records that tenant-owned job metadata has been tombstoned.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param transitionedAt instant when the state became durable
     * @return updated receipt
     */
    ArtifactDeletionReceipt markMetadataTombstoned(UUID jobId, Instant transitionedAt);

    /**
     * Marks exact-digest artifact cleanup ready for a worker attempt.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param transitionedAt instant when the state became durable
     * @return updated receipt
     */
    ArtifactDeletionReceipt markCleanupPending(UUID jobId, Instant transitionedAt);

    /**
     * Records one controlled cleanup failure while retaining retryable evidence.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param failureCode controlled non-sensitive failure code
     * @param attemptedAt failed attempt instant
     * @return updated failed receipt
     */
    ArtifactDeletionReceipt recordCleanupFailure(
            UUID jobId,
            String failureCode,
            Instant attemptedAt
    );

    /**
     * Records successful terminal cleanup of the exact artifact digest.
     *
     * @param jobId permanently reserved conversion-job identifier
     * @param completedAt completion instant
     * @return updated terminal receipt
     */
    ArtifactDeletionReceipt markCleanupCompleted(UUID jobId, Instant completedAt);

    /**
     * Finds the latest receipt for a job identifier.
     *
     * @param jobId conversion-job identifier
     * @return receipt when present
     */
    Optional<ArtifactDeletionReceipt> findByJobId(UUID jobId);

    /**
     * Returns all incomplete receipts in deterministic request order.
     *
     * @return pending and failed receipts
     */
    List<ArtifactDeletionReceipt> pendingReceipts();

    /**
     * Returns the number of incomplete receipts.
     *
     * @return pending and failed receipt count
     */
    int pendingCount();
}
