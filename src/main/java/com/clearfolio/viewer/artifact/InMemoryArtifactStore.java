package com.clearfolio.viewer.artifact;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ArtifactStore}.
 *
 * <p>Contents do not survive restarts; this implementation backs tests and the
 * {@code clearfolio.artifact-store.mode=in-memory} configuration, while the
 * disk-backed {@link FileSystemArtifactStore} is the runtime default.
 */
public class InMemoryArtifactStore implements ArtifactStore {

    private final ConcurrentHashMap<UUID, byte[]> pdfByDocId = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void putPdf(UUID docId, byte[] pdfBytes) {
        pdfByDocId.put(docId, pdfBytes.clone());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<byte[]> getPdf(UUID docId) {
        byte[] bytes = pdfByDocId.get(docId);
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(bytes.clone());
    }
}
