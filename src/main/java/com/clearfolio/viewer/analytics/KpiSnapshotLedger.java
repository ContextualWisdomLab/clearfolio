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
    private static final String NULL_FIELD = "-";
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
                snapshot.conversionSuccessRate(),
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
            return;
        } catch (IOException | UncheckedIOException ex) {
            throw new IllegalStateException(
                    "kpi snapshot ledger cannot be loaded", ex);
        }
    }

    private void replayLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 12 || !SNAPSHOT.equals(fields[0])) {
            throw invalidLine();
        }
        snapshots.add(new KpiSnapshotRecord(
                requiredValue(fields[1]),
                requiredValue(fields[2]),
                instant(fields[3]),
                integer(fields[4]),
                integer(fields[5]),
                integer(fields[6]),
                integer(fields[7]),
                integer(fields[8]),
                integer(fields[9]),
                rate(fields[10]),
                nullableLong(fields[11])
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
            return Double.parseDouble(field);
        } catch (NumberFormatException ex) {
            throw invalidLine(ex);
        }
    }

    private static Long nullableLong(String field) {
        if (NULL_FIELD.equals(field)) {
            return null;
        }
        try {
            return Long.parseLong(field);
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
