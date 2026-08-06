package com.clearfolio.viewer.artifact;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.lifecycle.ArtifactDeletionReceiptStore;
import com.clearfolio.viewer.lifecycle.ArtifactLifecycleLockRegistry;

/**
 * Artifact-store decorator that fences writes after deletion intent is durable.
 *
 * <p>Every artifact operation is serialized through the same per-job lifecycle
 * lock used by the deletion coordinator. A write is rejected as soon as a
 * durable deletion receipt exists, including while cleanup is pending or failed,
 * so an already-running conversion cannot recreate bytes after deletion
 * completion in the reference process.</p>
 *
 * <p>Remote or multi-instance adapters must provide the equivalent object
 * generation precondition or distributed lifecycle fence.</p>
 */
public final class LifecycleFencedArtifactStore implements ArtifactStore {

    private final ArtifactStore delegate;
    private final ArtifactDeletionReceiptStore receiptStore;
    private final ArtifactLifecycleLockRegistry lifecycleLocks;

    /**
     * Creates a standalone deletion-aware artifact boundary using the shared
     * process-local lifecycle locks.
     *
     * @param delegate underlying artifact store
     * @param receiptStore durable deletion receipt store
     * @throws NullPointerException when a required collaborator is absent
     */
    public LifecycleFencedArtifactStore(
            ArtifactStore delegate,
            ArtifactDeletionReceiptStore receiptStore
    ) {
        this(delegate, receiptStore, ArtifactLifecycleLockRegistry.shared());
    }

    /**
     * Creates a deletion-aware artifact-store boundary.
     *
     * @param delegate underlying artifact store
     * @param receiptStore durable deletion receipt store
     * @param lifecycleLocks per-job lifecycle serialization boundary
     * @throws NullPointerException when a required collaborator is absent
     */
    public LifecycleFencedArtifactStore(
            ArtifactStore delegate,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactLifecycleLockRegistry lifecycleLocks
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.receiptStore = Objects.requireNonNull(receiptStore, "receiptStore");
        this.lifecycleLocks = Objects.requireNonNull(lifecycleLocks, "lifecycleLocks");
    }

    /**
     * Stores bytes only while no durable deletion receipt exists.
     *
     * @param docId document identifier
     * @param pdfBytes complete PDF bytes
     * @throws IllegalStateException when deletion intent already exists
     */
    @Override
    public void putPdf(UUID docId, byte[] pdfBytes) {
        lifecycleLocks.withJobLock(docId, () -> {
            if (receiptStore.findByJobId(docId).isPresent()) {
                throw new IllegalStateException("artifact write rejected for deleted lifecycle");
            }
            delegate.putPdf(docId, pdfBytes);
            return null;
        });
    }

    /**
     * Reads bytes under the per-job lifecycle lock.
     *
     * @param docId document identifier
     * @return stored bytes when present
     */
    @Override
    public Optional<byte[]> getPdf(UUID docId) {
        return lifecycleLocks.withJobLock(docId, () -> delegate.getPdf(docId));
    }

    /**
     * Deletes bytes under the per-job lifecycle lock.
     *
     * @param docId document identifier
     */
    @Override
    public void deletePdf(UUID docId) {
        lifecycleLocks.withJobLock(docId, () -> {
            delegate.deletePdf(docId);
            return null;
        });
    }
}
