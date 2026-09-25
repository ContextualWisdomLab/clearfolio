package com.clearfolio.viewer.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.api.KpiSnapshotResponse;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;

class KpiSnapshotLedgerTest {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    private Path tempDir;

    @Test
    void recordsSnapshotsInMemoryAndFiltersByTenant() {
        KpiSnapshotLedger ledger = new KpiSnapshotLedger();

        ledger.recordSnapshot(context("tenant-a"), snapshot(2, 1, 123L));
        ledger.recordSnapshot(context("tenant-b"), snapshot(1, 0, null));

        assertEquals(1, ledger.snapshotsFor("tenant-a").size());
        assertEquals(123L, ledger.snapshotsFor("tenant-a").getFirst().p95TimeToPreviewMs());
        assertEquals(1, ledger.snapshotsFor("tenant-b").size());
        assertTrue(ledger.snapshotsFor("tenant-c").isEmpty());
    }

    @Test
    void persistsAndReloadsSnapshotsWhenPathIsConfigured() {
        Path ledgerPath = tempDir.resolve("kpi-snapshots.log");
        KpiSnapshotLedger ledger = new KpiSnapshotLedger(ledgerPath, CLOCK);

        ledger.recordSnapshot(context("tenant-a"), snapshot(2, 1, 123L));
        ledger.recordSnapshot(context("tenant-a"), snapshot(0, 0, null));

        KpiSnapshotLedger reloaded = new KpiSnapshotLedger(ledgerPath, CLOCK);
        assertEquals(2, reloaded.snapshotsFor("tenant-a").size());
        assertEquals(NOW, reloaded.snapshotsFor("tenant-a").getFirst().exportedAt());
        assertEquals(123L, reloaded.snapshotsFor("tenant-a").getFirst().p95TimeToPreviewMs());
        assertNull(reloaded.snapshotsFor("tenant-a").get(1).p95TimeToPreviewMs());
    }

    @Test
    void stringConstructorTreatsBlankPathAsInMemoryAndMissingPathAsEmpty() {
        KpiSnapshotLedger nullPath = new KpiSnapshotLedger((String) null);
        KpiSnapshotLedger blankPath = new KpiSnapshotLedger(" ");
        KpiSnapshotLedger configuredStringPath = new KpiSnapshotLedger(
                tempDir.resolve("configured-string.log").toString()
        );
        KpiSnapshotLedger missingPath = new KpiSnapshotLedger(tempDir.resolve("missing.log"), CLOCK);

        assertTrue(nullPath.snapshotsFor("tenant-a").isEmpty());
        assertTrue(blankPath.snapshotsFor("tenant-a").isEmpty());
        assertTrue(configuredStringPath.snapshotsFor("tenant-a").isEmpty());
        assertTrue(missingPath.snapshotsFor("tenant-a").isEmpty());
    }

    @Test
    void rejectsInvalidPersistedLedgerLines() throws Exception {
        assertInvalidLedger("BROKEN");
        assertInvalidLedger("SNAPSHOT\ttoo-short");
        assertInvalidLedger(snapshotLine().replace("SNAPSHOT", "BROKEN"));
        assertInvalidLedger(snapshotLine().replace(encoded("tenant-a"), "-"));
        assertInvalidLedger(snapshotLine().replace(encoded("subject-a"), encoded(" ")));
        assertInvalidLedger(snapshotLine().replace(NOW.toString(), "not-instant"));
        assertInvalidLedger(snapshotLine().replace("\t2\t", "\tnot-int\t"));
        assertInvalidLedger(snapshotLine().replace("\t0.5\t", "\tnot-rate\t"));
        assertInvalidLedger(snapshotLine().replace("\t123", "\tnot-long"));
        assertInvalidLedger(snapshotLine().replace(encoded("tenant-a"), "!"));
    }

    @Test
    void reportsLoadAndWriteFailures() throws Exception {
        Path directory = tempDir.resolve("directory-ledger");
        Files.createDirectory(directory);
        assertThrows(IllegalStateException.class, () -> new KpiSnapshotLedger(directory, CLOCK));

        Path blockedParent = tempDir.resolve("blocked-parent");
        Path ledgerPath = blockedParent.resolve("ledger.log");
        KpiSnapshotLedger ledger = new KpiSnapshotLedger(ledgerPath, CLOCK);
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> ledger.recordSnapshot(context("tenant-a"), snapshot(1, 1, null)));
    }

    private void assertInvalidLedger(String line) throws Exception {
        Path ledgerPath = Files.writeString(
                tempDir.resolve(UUID.randomUUID() + ".log"),
                line + System.lineSeparator(),
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, () -> new KpiSnapshotLedger(ledgerPath, CLOCK));
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "subject-a", java.util.Set.of(TenantPermissions.ANALYTICS_READ));
    }

    private static KpiSnapshotResponse snapshot(int totalJobs, int succeededJobs, Long p95TimeToPreviewMs) {
        return new KpiSnapshotResponse(
                totalJobs,
                1,
                0,
                succeededJobs,
                1,
                0,
                0.5,
                p95TimeToPreviewMs
        );
    }

    private static String snapshotLine() {
        return String.join("\t",
                "SNAPSHOT",
                encoded("tenant-a"),
                encoded("subject-a"),
                NOW.toString(),
                "2",
                "1",
                "0",
                "1",
                "1",
                "0",
                "0.5",
                "123"
        );
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
