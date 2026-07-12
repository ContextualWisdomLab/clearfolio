package com.clearfolio.viewer.artifact;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores and retrieves preview artifacts produced by conversion jobs.
 *
 * <p>Implementations keep the hot request path CPU-only where possible; the
 * disk-backed implementation caches bytes in memory after the first access so
 * filesystem reads are limited to cache misses (for example, after a restart).
 */
public interface ArtifactStore {

    /**
     * Stores the converted PDF bytes for a document.
     *
     * @param docId document identifier
     * @param pdfBytes complete PDF file bytes
     */
    void putPdf(UUID docId, byte[] pdfBytes);

    /**
     * Reads stored PDF bytes for a document.
     *
     * @param docId document identifier
     * @return PDF bytes when present
     */
    Optional<byte[]> getPdf(UUID docId);

    /**
     * Deletes stored PDF bytes for a document.
     *
     * @param docId document identifier
     */
    void deletePdf(UUID docId);
}
