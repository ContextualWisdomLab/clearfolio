package com.clearfolio.viewer.lifecycle;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Append-only durable store for tenant-bound artifact-deletion receipts.
 *
 * <p>The file-backed mode writes a complete immutable receipt snapshot for each
 * legal transition and forces the file channel before returning. Startup replay
 * uses bounded strict UTF-8 lines and validates immutable identity, timestamps,
 * and monotonic transitions before exposing pending work. An empty path selects
 * the standalone in-memory adapter.</p>
 */
@Repository
public class ArtifactDeletionLedger implements ArtifactDeletionReceiptStore {

    static final int MAX_LEDGER_LINE_BYTES = 16 * 1024;

    private static final String RECEIPT = "RECEIPT_V1";
    private static final String NULL_FIELD = "-";
    private static final int FIELD_COUNT = 13;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Comparator<ArtifactDeletionReceipt> RECEIPT_ORDER = Comparator
            .comparing(ArtifactDeletionReceipt::requestedAt)
            .thenComparing(receipt -> receipt.jobId().toString());

    private final ConcurrentMap<UUID, ArtifactDeletionReceipt> receiptsByJobId = new ConcurrentHashMap<>();
    private final Path ledgerPath;

    /**
     * Creates a standalone in-memory deletion ledger.
     */
    public ArtifactDeletionLedger() {
        this((Path) null);
    }

    /**
     * Creates a deletion ledger with optional append-only file persistence.
     *
     * @param configuredPath configured ledger path, or blank for in-memory mode
     */
    @Autowired
    public ArtifactDeletionLedger(
            @Value("${clearfolio.artifact-deletion-ledger.path:data/artifact-deletion-receipts.log}")
            String configuredPath
    ) {
        this(pathOf(configuredPath));
    }

    ArtifactDeletionLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
        load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ArtifactDeletionReceipt request(
            UUID requestId,
            String tenantId,
            UUID jobId,
            String artifactChecksum,
            String auditCorrelationId,
            Instant requestedAt
    ) {
        ArtifactDeletionReceipt requested = new ArtifactDeletionReceipt(
                requestId,
                tenantId,
                jobId,
                artifactChecksum,
                auditCorrelationId,
                requestedAt,
                requestedAt,
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                null
        );
        ArtifactDeletionReceipt existing = receiptsByJobId.get(requested.jobId());
        if (existing != null) {
            if (existing.hasSameIdentity(requested)) {
                return existing;
            }
            throw conflictingReceipt();
        }

        append(requested);
        receiptsByJobId.put(requested.jobId(), requested);
        return requested;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactDeletionReceipt markMetadataTombstoned(UUID jobId, Instant transitionedAt) {
        return transition(jobId, current -> current.markMetadataTombstoned(transitionedAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactDeletionReceipt markCleanupPending(UUID jobId, Instant transitionedAt) {
        return transition(jobId, current -> current.markCleanupPending(transitionedAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactDeletionReceipt recordCleanupFailure(
            UUID jobId,
            String failureCode,
            Instant attemptedAt
    ) {
        return transition(
                jobId,
                current -> current.recordCleanupFailure(failureCode, attemptedAt)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactDeletionReceipt markCleanupCompleted(UUID jobId, Instant completedAt) {
        return transition(jobId, current -> current.markCleanupCompleted(completedAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ArtifactDeletionReceipt> findByJobId(UUID jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(receiptsByJobId.get(jobId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ArtifactDeletionReceipt> pendingReceipts() {
        return receiptsByJobId.values().stream()
                .filter(receipt -> !receipt.isCompleted())
                .sorted(RECEIPT_ORDER)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int pendingCount() {
        return Math.toIntExact(receiptsByJobId.values().stream()
                .filter(receipt -> !receipt.isCompleted())
                .count());
    }

    private synchronized ArtifactDeletionReceipt transition(
            UUID jobId,
            UnaryOperator<ArtifactDeletionReceipt> transition
    ) {
        UUID requiredJobId = Objects.requireNonNull(jobId, "jobId");
        ArtifactDeletionReceipt current = receiptsByJobId.get(requiredJobId);
        if (current == null) {
            throw missingReceipt();
        }
        ArtifactDeletionReceipt updated = Objects.requireNonNull(
                transition.apply(current),
                "updatedReceipt"
        );
        if (!current.hasSameIdentity(updated)) {
            throw conflictingReceipt();
        }
        append(updated);
        receiptsByJobId.put(requiredJobId, updated);
        return updated;
    }

    private void load() {
        if (ledgerPath == null) {
            return;
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(ledgerPath))) {
            readBoundedLines(input);
        } catch (java.nio.file.NoSuchFileException exception) {
            // A missing ledger represents an empty durable store.
        } catch (IOException exception) {
            throw new IllegalStateException("artifact deletion ledger cannot be loaded", exception);
        }
    }

    private void readBoundedLines(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int currentByte;
        while ((currentByte = input.read()) != -1) {
            if (currentByte == '\n') {
                replayBytes(line.toByteArray());
                line.reset();
                continue;
            }
            if (line.size() >= MAX_LEDGER_LINE_BYTES) {
                throw oversizedLine();
            }
            line.write(currentByte);
        }
        if (line.size() > 0) {
            throw invalidLine();
        }
    }

    private void replayBytes(byte[] bytes) {
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        if (length == 0) {
            throw invalidLine();
        }
        replayLine(strictUtf8(Arrays.copyOf(bytes, length)));
    }

    private void replayLine(String line) {
        ArtifactDeletionReceipt replayed = parse(line);
        ArtifactDeletionReceipt current = receiptsByJobId.get(replayed.jobId());
        if (current == null) {
            if (replayed.state() != ArtifactDeletionState.DELETION_REQUESTED
                    || replayed.attemptCount() != 0) {
                throw invalidLine();
            }
            receiptsByJobId.put(replayed.jobId(), replayed);
            return;
        }
        if (!current.hasSameIdentity(replayed)) {
            throw invalidLine();
        }
        validateReplayTransition(current, replayed);
        receiptsByJobId.put(replayed.jobId(), replayed);
    }

    private void append(ArtifactDeletionReceipt receipt) {
        if (ledgerPath == null) {
            return;
        }
        String serialized = serialize(receipt);
        byte[] bytes = (serialized + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            Path absolutePath = ledgerPath.toAbsolutePath();
            Files.createDirectories(absolutePath.getParent());
            try (FileChannel channel = FileChannel.open(
                    absolutePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("artifact deletion ledger cannot be written", exception);
        }
    }

    private static ArtifactDeletionReceipt parse(String line) {
        try {
            String[] fields = line.split("\\t", -1);
            if (fields.length != FIELD_COUNT || !RECEIPT.equals(fields[0])) {
                throw invalidLine();
            }
            return new ArtifactDeletionReceipt(
                    UUID.fromString(fields[1]),
                    decodeRequired(fields[2]),
                    UUID.fromString(fields[3]),
                    decodeRequired(fields[4]),
                    decodeRequired(fields[5]),
                    Instant.parse(fields[6]),
                    Instant.parse(fields[7]),
                    ArtifactDeletionState.valueOf(fields[8]),
                    Integer.parseInt(fields[9]),
                    optionalInstant(fields[10]),
                    optionalInstant(fields[11]),
                    decodeOptional(fields[12])
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidLine(exception);
        }
    }

    private static String serialize(ArtifactDeletionReceipt receipt) {
        return String.join(
                "\t",
                RECEIPT,
                receipt.requestId().toString(),
                encode(receipt.tenantId()),
                receipt.jobId().toString(),
                encode(receipt.artifactChecksum()),
                encode(receipt.auditCorrelationId()),
                receipt.requestedAt().toString(),
                receipt.stateChangedAt().toString(),
                receipt.state().name(),
                Integer.toString(receipt.attemptCount()),
                optionalInstant(receipt.lastAttemptAt()),
                optionalInstant(receipt.completedAt()),
                encodeOptional(receipt.failureCode())
        );
    }

    private static void validateReplayTransition(
            ArtifactDeletionReceipt current,
            ArtifactDeletionReceipt replayed
    ) {
        if (replayed.stateChangedAt().isBefore(current.stateChangedAt())) {
            throw invalidLine();
        }
        boolean valid = switch (current.state()) {
            case DELETION_REQUESTED -> replayed.state() == ArtifactDeletionState.METADATA_TOMBSTONED
                    && hasSameAttemptEvidence(current, replayed);
            case METADATA_TOMBSTONED -> replayed.state() == ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING
                    && hasSameAttemptEvidence(current, replayed);
            case ARTIFACT_CLEANUP_PENDING -> (
                    replayed.state() == ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED
                            && hasSameAttemptEvidence(current, replayed)
            ) || (
                    replayed.state() == ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED
                            && replayed.attemptCount() == current.attemptCount() + 1
            );
            case ARTIFACT_CLEANUP_FAILED -> replayed.state() == ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING
                    && hasSameAttemptEvidence(current, replayed);
            case ARTIFACT_CLEANUP_COMPLETED -> false;
        };
        if (!valid) {
            throw invalidLine();
        }
    }

    private static boolean hasSameAttemptEvidence(
            ArtifactDeletionReceipt current,
            ArtifactDeletionReceipt replayed
    ) {
        return replayed.attemptCount() == current.attemptCount()
                && Objects.equals(replayed.lastAttemptAt(), current.lastAttemptAt());
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalidLine(exception);
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeOptional(String value) {
        return value == null ? NULL_FIELD : encode(value);
    }

    private static String decodeRequired(String field) {
        String value = decodeOptional(field);
        if (value == null || value.isBlank()) {
            throw invalidLine();
        }
        return value;
    }

    private static String decodeOptional(String field) {
        if (NULL_FIELD.equals(field)) {
            return null;
        }
        return strictUtf8(DECODER.decode(field));
    }

    private static String optionalInstant(Instant value) {
        return value == null ? NULL_FIELD : value.toString();
    }

    private static Instant optionalInstant(String value) {
        return NULL_FIELD.equals(value) ? null : Instant.parse(value);
    }

    private static Path pathOf(String value) {
        String normalized = value == null ? null : value.strip();
        return normalized == null || normalized.isEmpty() ? null : Path.of(normalized);
    }

    private static IllegalStateException conflictingReceipt() {
        return new IllegalStateException("artifact deletion receipt conflicts with an existing lifecycle");
    }

    private static IllegalStateException missingReceipt() {
        return new IllegalStateException("artifact deletion receipt not found");
    }

    private static IllegalStateException oversizedLine() {
        return new IllegalStateException("artifact deletion ledger line exceeds the configured bound");
    }

    private static IllegalStateException invalidLine() {
        return new IllegalStateException("artifact deletion ledger contains an invalid line");
    }

    private static IllegalStateException invalidLine(Throwable cause) {
        return new IllegalStateException("artifact deletion ledger contains an invalid line", cause);
    }
}
