package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Verifies the durable deletion state vocabulary used by lifecycle receipts.
 */
class ArtifactDeletionStateTest {

    @Test
    void exposesOnlyTheMonotonicDeletionLifecycleStates() {
        ArtifactDeletionState[] expected = {
            ArtifactDeletionState.DELETION_REQUESTED,
            ArtifactDeletionState.METADATA_TOMBSTONED,
            ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
            ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
            ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED
        };

        assertArrayEquals(expected, ArtifactDeletionState.values());
        for (ArtifactDeletionState state : expected) {
            assertSame(state, ArtifactDeletionState.valueOf(state.name()));
        }
    }
}
