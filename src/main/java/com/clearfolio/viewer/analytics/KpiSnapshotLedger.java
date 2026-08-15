package com.clearfolio.viewer.analytics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.clearfolio.viewer.api.KpiSnapshotResponse;
import com.clearfolio.viewer.auth.TenantContext;

/**
 * Append-only evidence ledger for exported KPI snapshots.
 */
@Repository
public class KpiSnapshotLedger {

    private static final String SNAPSHOT = "SNAPSHOT";
    private static final String TERMINAL_OUTCOMES_RATE_VERSION = "terminal-outcomes-v1";
    private static final String NULL_FIELD = "-";
    private static final int LEGACY_FIELD_COUNT = 12;
    private static final int CURRENT_FIELD_COUNT = 13;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ConcurrentLinkedQueue<KpiSnapshotRecord> snapshots = new ConcurrentLinkedQueue<>();
    private final Path ledgerPath;
    private final Clock clock;

    /**
     * Creates an in-memory KPI snapshot ledger.
     */
    public KpiSnapshotLedger() {
        this(null, Clock.systemUTC());
    }

    /**
     * Creates a KPI snapshot ledger with optional file-backed persistence.
     *
     * @param ledgerPath configured append-only ledger path
     */
    @Autowired
    public KpiSnapshotLedger(@Value("${clearfolio.analytics-snapshot-ledger.path:}") String ledgerPath) {
        this(pathOf(ledgerPath), Clock.systemUTC());
    }

    KpiSnapshotLedger(Path ledgerPath, Clock clock) {
        this.ledgerPath = ledgerPath;
        this.clock = clock;
        load();
    }

    /**
     * Records a KPI snapshot export.
     *
     * @param tenantContext tenant and subject that requested the snapshot
     * @param snapshot KPI payload returned to the caller
     */
    public synchronized void recordSnapshot(TenantContext tenantContext, KpiSnapshotResponse snapshot) {
        KpiSnapshotRecord record = new KpiSnapshotRecord(
                tenantContext.tenantId(),
                tenantContext.subjectId(),
                Instant.now(clock),
                snapshot.totalJobs(),
                snapshot.submittedJobs(),
                snapshot.processingJobs(),
                snapshot.succeededJobs(),
                snapshot.failedJobs(),
                snapshot.deadLetteredJobs(),
                terminalSuccessRate(snapshot.succeededJobs(), snapshot.failedJobs()),
                snapshot.p95TimeToPreviewMs()
        );
        snapshots.add(record);
        appendLine(serialize(record));
    }

    /**
     * Returns KPI snapshot evidence for a tenant.
     *
     * @param tenantId tenant identifier
     * @return matching snapshot evidence
     */
    public List<KpiSnapshotRecord> snapshotsFor(String tenantId) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.tenantId().equals(tenantId))
                .toList();
    }

    private void load() {
        if (ledgerPath == null) {
            return;
        }
        try (Stream<String> lines = Files.lines(ledgerPath, StandardCharsets.UTF_8)) {
            lines.forEach(this::replayLine);
        } catch (java.nio.file.NoSuchFileException ex) {
            // Ignore missing ledger file
        } catch (IOException | UncheckedIOException ex) {
            throw new IllegalStateException("kpi snapshot ledger cannot be loaded", ex);
        }
    }

    private void replayLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length == LEGACY_FIELD_COUNT && SNAPSHOT.equals(fields[0])) {
            replayLegacySnapshot(fields);
            return;
        }
        if (fields.length == CURRENT_FIELD_COUNT
                && SNAPSHOT.equals(fields[0])
                && TERMINAL_OUTCOMES_RATE_VERSION.equals(fields[1])) {
            replayCurrentSnapshot(fields);
            return;
        }
        throw invalidLine();
    }

    private void replayLegacySnapshot(String[] fields) {
        int totalJobs = integer(fields[4]);
        int submittedJobs = integer(fields[5]);
        int processingJobs = integer(fields[6]);
        int succeededJobs = integer(fields[7]);
        int failedJobs = integer(fields[8]);
        int deadLetteredJobs = integer(fields[9]);
        requireRate(rate(fields[10]), totalSuccessRate(totalJobs, succeededJobs));
        snapshots.add(new KpiSnapshotRecord(
                requiredValue(fields[1]),
                requiredValue(fields[2]),
                instant(fields[3]),
                totalJobs,
                submittedJobs,
                processingJobs,
                succeededJobs,
                failedJobs,
                deadLetteredJobs,
                terminalSuccessRate(succeededJobs, failedJobs),
                nullableLong(fields[11])
        ));
    }

    private void replayCurrentSnapshot(String[] fields) {
        int totalJobs = integer(fields[5]);
        int submittedJobs = integer(fields[6]);
        int processingJobs = integer(fields[7]);
        int succeededJobs = integer(fields[8]);
        int failedJobs = integer(fields[9]);
        int deadLetteredJobs = integer(fields[10]);
        double currentRate = terminalSuccessRate(succeededJobs, failedJobs);
        requireRate(rate(fields[11]), currentRate);
        snapshots.add(new KpiSnapshotRecord(
                requiredValue(fields[2]),
                requiredValue(fields[3]),
                instant(fields[4]),
                totalJobs,
                submittedJobs,
                processingJobs,
                succeededJobs,
                failedJobs,
                deadLetteredJobs,
                currentRate,
                nullableLong(fields[12])
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
            throw new IllegalStateException("kpi snapshot ledger cannot be written", ex);
        }
    }

    private static String serialize(KpiSnapshotRecord record) {
        return String.join("\t",
                SNAPSHOT,
                TERMINAL_OUTCOMES_RATE_VERSION,
                field(record.tenantId()),
                field(record.subjectId()),
                field(record.exportedAt()),
                String.valueOf(record.totalJobs()),
                String.valueOf(record.submittedJobs()),
                String.valueOf(record.processingJobs()),
                String.valueOf(record.succeededJobs()),
                String.valueOf(record.failedJobs()),
                String.valueOf(record.deadLetteredJobs()),
                String.valueOf(record.conversionSuccessRate()),
                field(record.p95TimeToPreviewMs())
        );
    }

    private static double totalSuccessRate(int totalJobs, int succeededJobs) {
        return totalJobs == 0 ? 0.0 : (double) succeededJobs / totalJobs;
    }

    private static double terminalSuccessRate(int succeededJobs, int failedJobs) {
        int terminalJobs = succeededJobs + failedJobs;
        return terminalJobs == 0 ? 0.0 : (double) succeededJobs / terminalJobs;
    }

    private static void requireRate(double storedRate, double expectedRate) {
        if (Double.compare(storedRate, expectedRate) != 0) {
            throw invalidLine();
        }
    }

    private static String field(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String field(Instant instant) {
        return instant.toString();
    }

    private static String field(Long value) {
        return value == null ? NULL_FIELD : String.valueOf(value);
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

    private static Instant instant(String field) {
        try {
            return Instant.parse(field);
        } catch (DateTimeException ex) {
            throw invalidLine(ex);
        }
    }

    private static int integer(String field) {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException ex) {
            throw invalidLine(ex);
        }
    }

    private static double rate(String field) {
        try {
            double parsed = Double.parseDouble(field);
            if (!Double.isFinite(parsed) || parsed < 0.0 || parsed > 1.0) {
                throw invalidLine();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalidLine(ex);
        }
    }

    private static Long nullableLong(String field) {
        if (NULL_FIELD.equals(field)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(field);
            if (parsed < 0L) {
                throw invalidLine();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalidLine(ex);
        }
    }

    private static Path pathOf(String value) {
        String cleaned = value == null ? null : value.strip();
        return cleaned == null || cleaned.isEmpty() ? null : Path.of(cleaned);
    }

    private static IllegalStateException invalidLine() {
        return new IllegalStateException("kpi snapshot ledger contains an invalid line");
    }

    private static IllegalStateException invalidLine(Throwable cause) {
        return new IllegalStateException("kpi snapshot ledger contains an invalid line", cause);
    }
}
