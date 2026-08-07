package com.clearfolio.viewer.lifecycle;

/**
 * Durable states in the artifact-deletion receipt lifecycle.
 */
public enum ArtifactDeletionState {
    /**
     * An authorized deletion request has been durably accepted. The receipt may
     * still carry the controlled pre-snapshot marker until an exact artifact
     * checksum is captured; metadata cannot be tombstoned before that binding.
     */
    DELETION_REQUESTED,

    /**
     * Tenant-owned job metadata has been tombstoned.
     */
    METADATA_TOMBSTONED,

    /**
     * Exact-generation artifact cleanup is ready for a worker attempt.
     */
    ARTIFACT_CLEANUP_PENDING,

    /**
     * Artifact cleanup completed and the receipt is terminal.
     */
    ARTIFACT_CLEANUP_COMPLETED,

    /**
     * Artifact cleanup failed and remains eligible for a controlled retry.
     */
    ARTIFACT_CLEANUP_FAILED
}
