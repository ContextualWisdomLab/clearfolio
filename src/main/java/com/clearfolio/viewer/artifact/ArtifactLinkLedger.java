package com.clearfolio.viewer.artifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Runtime ledger for issued artifact links and artifact read audit events.
 */
@Repository
public class ArtifactLinkLedger {

    private static final String ISSUED = "ISSUED";
    private static final String REVOKED = "REVOKED";
    private static final String READ = "READ";
    private static final String NULL_FIELD = "-";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ConcurrentMap<String, ArtifactLinkRecord> issuedLinks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ArtifactReadEvent> readEvents = new ConcurrentLinkedQueue<>();
    private final Path ledgerPath;

    /**
     * Creates an in-memory artifact link ledger.
     */
    public ArtifactLinkLedger() {
        this((Path) null);
    }

    /**
     * Creates an artifact link ledger with optional file-backed persistence.
     *
     * @param ledgerPath configured append-only ledger path
     */
    @Autowired
    public ArtifactLinkLedger(@Value("${clearfolio.artifact-link-ledger.path:}") String ledgerPath) {
        this(pathOf(ledgerPath));
    }

    ArtifactLinkLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
        load();
    }

    /**
     * Records an issued artifact link.
     *
     * @param record issued artifact link record
     */
    public synchronized void recordIssued(ArtifactLinkRecord record) {
        issuedLinks.put(record.tokenId(), record);
        appendLine(serializeIssued(record));
    }

    /**
     * Finds an issued artifact link by token identifier.
     *
     * @param tokenId token identifier
     * @return matching record when present
     */
    public Optional<ArtifactLinkRecord> findByTokenId(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(issuedLinks.get(tokenId));
    }

    /**
     * Marks an issued artifact link as revoked.
     *
     * @param tokenId token identifier
     * @param revokedAt revocation timestamp
     * @param revokedBy subject requesting revocation
     * @param reason recorded reason
     * @return updated record when the token exists
     */
    public synchronized Optional<ArtifactLinkRecord> revoke(
            String tokenId,
            Instant revokedAt,
            String revokedBy,
            String reason) {
        boolean[] changed = {false};
        ArtifactLinkRecord revoked = issuedLinks.computeIfPresent(tokenId, (ignored, current) -> {
            if (current.isRevoked()) {
                return current;
            }
            changed[0] = true;
            return current.revoked(revokedAt, revokedBy, reason);
        });
        if (changed[0]) {
            appendLine(serializeRevoked(revoked));
        }
        return Optional.ofNullable(revoked);
    }

    /**
     * Records a verified artifact read.
     *
     * @param event artifact read event
     */
    public synchronized void recordRead(ArtifactReadEvent event) {
        readEvents.add(event);
        appendLine(serializeRead(event));
    }

    /**
     * Returns read events for a tenant-owned document.
     *
     * @param tenantId tenant identifier
     * @param docId document identifier
     * @return current matching read events
     */
    public List<ArtifactReadEvent> readEventsFor(String tenantId, UUID docId) {
        return readEvents.stream()
                .filter(event -> event.tenantId().equals(tenantId))
                .filter(event -> event.docId().equals(docId))
                .toList();
    }

    private void load() {
        if (ledgerPath == null) {
            return;
        }
        try (Stream<String> lines = Files.lines(ledgerPath, StandardCharsets.UTF_8)) {
            lines.forEach(this::replayLine);
        } catch (java.nio.file.NoSuchFileException ex) {
            return;
        } catch (IOException | UncheckedIOException ex) {
            throw new IllegalStateException(
                    "artifact link ledger cannot be loaded", ex);
        }
    }

    private void replayLine(String line) {
        String[] fields = line.split("\t", -1);
        switch (fields[0]) {
            case ISSUED -> replayIssued(fields);
            case REVOKED -> replayRevoked(fields);
            case READ -> replayRead(fields);
            default -> throw invalidLine();
        }
    }

    private void replayIssued(String[] fields) {
        if (fields.length != 14) {
            throw invalidLine();
        }
        ArtifactLinkRecord record = new ArtifactLinkRecord(
                requiredValue(fields[1]),
                requiredValue(fields[2]),
                requiredValue(fields[3]),
                uuid(fields[4]),
                requiredValue(fields[5]),
                requiredValue(fields[6]),
                requiredValue(fields[7]),
                value(fields[8]),
                instant(fields[9]),
                instant(fields[10]),
                instant(fields[11]),
                value(fields[12]),
                value(fields[13])
        );
        issuedLinks.put(record.tokenId(), record);
    }

    private void replayRevoked(String[] fields) {
        if (fields.length != 5) {
            throw invalidLine();
        }
        String tokenId = requiredValue(fields[1]);
        ArtifactLinkRecord current = issuedLinks.get(tokenId);
        if (current == null) {
            throw invalidLine();
        }
        issuedLinks.put(tokenId, current.revoked(
                instant(fields[2]),
                value(fields[3]),
                value(fields[4])
        ));
    }

    private void replayRead(String[] fields) {
        if (fields.length != 9) {
            throw invalidLine();
        }
        readEvents.add(new ArtifactReadEvent(
                requiredValue(fields[1]),
                requiredValue(fields[2]),
                uuid(fields[3]),
                requiredValue(fields[4]),
                value(fields[5]),
                statusCode(fields[6]),
                value(fields[7]),
                instant(fields[8])
        ));
    }

    private void appendLine(String line) {
        if (ledgerPath == null) {
            return;
        }
        try {
            Files.createDirectories(ledgerPath.toAbsolutePath().getParent());
            Files.writeString(
                    ledgerPath,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            throw new IllegalStateException("artifact link ledger cannot be written", ex);
        }
    }

    private static String serializeIssued(ArtifactLinkRecord record) {
        return String.join("\t",
                ISSUED,
                field(record.tokenId()),
                field(record.tenantId()),
                field(record.subjectId()),
                record.docId().toString(),
                field(record.scope()),
                field(record.purpose()),
                field(record.artifactChecksum()),
                field(record.viewerSessionId()),
                field(record.issuedAt()),
                field(record.expiresAt()),
                field(record.revokedAt()),
                field(record.revokedBy()),
                field(record.revokeReason())
        );
    }

    private static String serializeRevoked(ArtifactLinkRecord record) {
        return String.join("\t",
                REVOKED,
                field(record.tokenId()),
                field(record.revokedAt()),
                field(record.revokedBy()),
                field(record.revokeReason())
        );
    }

    private static String serializeRead(ArtifactReadEvent event) {
        return String.join("\t",
                READ,
                field(event.tenantId()),
                field(event.subjectId()),
                event.docId().toString(),
                field(event.tokenId()),
                field(event.rangeRequested()),
                String.valueOf(event.statusCode()),
                field(event.traceId()),
                field(event.readAt())
        );
    }

    private static String field(String value) {
        return value == null
                ? NULL_FIELD
                : ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String field(Instant instant) {
        return instant == null ? NULL_FIELD : instant.toString();
    }

    private static String value(String field) {
        if (NULL_FIELD.equals(field)) {
            return null;
        }
        try {
            return new String(DECODER.decode(field), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw invalidLine(ex);
        }
    }

    private static String requiredValue(String field) {
        String value = value(field);
        if (value == null || value.isBlank()) {
            throw invalidLine();
        }
        return value;
    }

    private static UUID uuid(String field) {
        try {
            return UUID.fromString(field);
        } catch (IllegalArgumentException ex) {
            throw invalidLine(ex);
        }
    }

    private static int statusCode(String field) {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException ex) {
            throw invalidLine(ex);
        }
    }

    private static Instant instant(String field) {
        if (NULL_FIELD.equals(field)) {
            return null;
        }
        try {
            return Instant.parse(field);
        } catch (DateTimeException ex) {
            throw invalidLine(ex);
        }
    }

    private static Path pathOf(String value) {
        String cleaned = value == null ? null : value.strip();
        return cleaned == null || cleaned.isEmpty() ? null : Path.of(cleaned);
    }

    private static IllegalStateException invalidLine() {
        return new IllegalStateException("artifact link ledger contains an invalid line");
    }

    private static IllegalStateException invalidLine(Throwable cause) {
        return new IllegalStateException("artifact link ledger contains an invalid line", cause);
    }
}
