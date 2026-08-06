package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.lifecycle.ArtifactDeletionLedger;
import com.clearfolio.viewer.lifecycle.ArtifactLifecycleLockRegistry;

/**
 * Verifies deletion fencing at the artifact storage boundary.
 */
class LifecycleFencedArtifactStoreTest {

    @Test
    void constructorRejectsMissingCollaborators() {
        ArtifactStore delegate = new InMemoryArtifactStore();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactLifecycleLockRegistry locks = new ArtifactLifecycleLockRegistry();

        assertThrows(
                NullPointerException.class,
                () -> new LifecycleFencedArtifactStore(null, ledger, locks)
        );
        assertThrows(
                NullPointerException.class,
                () -> new LifecycleFencedArtifactStore(delegate, null, locks)
        );
        assertThrows(
                NullPointerException.class,
                () -> new LifecycleFencedArtifactStore(delegate, ledger, null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new LifecycleFencedArtifactStore(null, ledger)
        );
    }

    @Test
    void sharedLockConstructorDelegatesNormalPutReadAndDelete() {
        LifecycleFencedArtifactStore store = new LifecycleFencedArtifactStore(
                new InMemoryArtifactStore(),
                new ArtifactDeletionLedger()
        );
        UUID jobId = UUID.randomUUID();
        byte[] bytes = new byte[] {1, 2, 3};

        store.putPdf(jobId, bytes);
        assertArrayEquals(bytes, store.getPdf(jobId).orElseThrow());
        store.deletePdf(jobId);

        assertTrue(store.getPdf(jobId).isEmpty());
    }

    @Test
    void durableDeletionReceiptRejectsEveryLaterArtifactWrite() {
        InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        LifecycleFencedArtifactStore store = new LifecycleFencedArtifactStore(
                delegate,
                ledger,
                new ArtifactLifecycleLockRegistry()
        );
        UUID jobId = UUID.randomUUID();
        ledger.request(
                UUID.randomUUID(),
                "tenant-a",
                jobId,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "cleanup-v1:fenced-write",
                Instant.now()
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.putPdf(jobId, new byte[] {9})
        );

        assertTrue(exception.getMessage().contains("deleted lifecycle"));
        assertTrue(delegate.getPdf(jobId).isEmpty());
    }

    @Test
    void missingIdentifiersFailClosedAtTheSharedLockBoundary() {
        LifecycleFencedArtifactStore store = new LifecycleFencedArtifactStore(
                new InMemoryArtifactStore(),
                new ArtifactDeletionLedger(),
                new ArtifactLifecycleLockRegistry()
        );

        assertThrows(NullPointerException.class, () -> store.putPdf(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> store.getPdf(null));
        assertThrows(NullPointerException.class, () -> store.deletePdf(null));
    }
}
