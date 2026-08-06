package com.clearfolio.viewer.lifecycle;

/**
 * Durable states in the artifact-deletion receipt lifecycle.
 */
public enum ArtifactDeletionState {
    /**
     * An authorized deletion request is durable, but the artifact generation
     * has not yet been read and bound to an exact checksum.
     */
    ARTIFACT_SNAPSHOT_PENDING,

    /**
     * The artifact generation could not be read and remains eligible for a
     * controlled retry while tenant-owned metadata is left intact.
     */
    ARTIFACT_SNAPSHOT_FAILED,

    /**
     * An authorized deletion request has an exact artifact checksum and is
     * ready for tenant-scoped metadata tombstoning.
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
