package com.clearfolio.viewer.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disk-backed {@link ArtifactStore} that persists artifact bytes and minimal
 * metadata under a configurable root directory so artifacts survive process
 * restarts.
 *
 * <p>Artifact bytes are cached in memory after the first write or read, so the
 * request-serving path only touches the filesystem when an artifact is not yet
 * cached (for example, on the first read after a restart).
 */
public final class FileSystemArtifactStore implements ArtifactStore {

    @FunctionalInterface
    interface BytesWriter {
        void write(Path path, byte[] bytes) throws IOException;
    }

    @FunctionalInterface
    interface BytesReader {
        byte[] read(Path path) throws IOException;
    }

    private static final String PDF_SUFFIX = ".pdf";
    private static final String METADATA_SUFFIX = ".meta.properties";

    private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();

    private final Path rootDir;
    private final BytesWriter bytesWriter;
    private final BytesReader bytesReader;
    private final ConcurrentHashMap<UUID, byte[]> cache = new ConcurrentHashMap<>();

    /**
     * Creates a store rooted at the supplied directory, creating it when absent.
     *
     * @param rootDir directory that holds artifact bytes and metadata files
     */
    public FileSystemArtifactStore(Path rootDir) {
        this(rootDir, (path, bytes) -> Files.write(path, bytes), Files::readAllBytes);
    }

    FileSystemArtifactStore(Path rootDir, BytesWriter bytesWriter, BytesReader bytesReader) {
        this.rootDir = rootDir;
        this.bytesWriter = bytesWriter;
        this.bytesReader = bytesReader;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create artifact storage directory: " + rootDir, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putPdf(UUID docId, byte[] pdfBytes) {
        byte[] copy = pdfBytes.clone();
        try {
            bytesWriter.write(pdfPath(docId), copy);
            bytesWriter.write(metadataPath(docId), metadataBytes(docId, copy));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to persist artifact for docId " + docId, ex);
        }
        cache.put(docId, copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<byte[]> getPdf(UUID docId) {
        byte[] cached = cache.get(docId);
        if (cached != null) {
            return Optional.of(cached.clone());
        }

        Path pdfPath = pdfPath(docId);
        if (!Files.exists(pdfPath)) {
            return Optional.empty();
        }

        try {
            byte[] loaded = bytesReader.read(pdfPath);
            cache.put(docId, loaded);
            return Optional.of(loaded.clone());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read artifact for docId " + docId, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deletePdf(UUID docId) {
        try {
            Files.deleteIfExists(pdfPath(docId));
            Files.deleteIfExists(metadataPath(docId));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete artifact for docId " + docId, ex);
        } finally {
            cache.remove(docId);
        }
    }

    private Path pdfPath(UUID docId) {
        return rootDir.resolve(docId + PDF_SUFFIX);
    }

    private Path metadataPath(UUID docId) {
        return rootDir.resolve(docId + METADATA_SUFFIX);
    }

    private static byte[] metadataBytes(UUID docId, byte[] pdfBytes) {
        String metadata = "docId=" + docId + "\n"
                + "sizeBytes=" + pdfBytes.length + "\n"
                + "sha256=" + sha256Hex(pdfBytes) + "\n"
                + "storedAt=" + Instant.now() + "\n";
        return metadata.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(final byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(bytes);
            return HEX_FORMAT.formatHex(raw);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
