package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Verifies receipt-first metadata tombstoning, exact-lifecycle cleanup, and recovery.
 */
class ArtifactDeletionCoordinatorTest {

    private static final String TENANT_ID = "tenant-north";
    private static final String OTHER_TENANT_ID = "tenant-south";
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final byte[] ORIGINAL_ARTIFACT = "%PDF-1.7\noriginal".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT_ARTIFACT = "%PDF-1.7\nreplacement".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void authorizedDeletionCompletesReceiptAndRemovesMetadataAndArtifact() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                registry,
                100
        );

        boolean deleted = coordinator.deleteForTenant(JOB_ID, TENANT_ID);

        assertTrue(deleted);
        assertTrue(repository.findById(JOB_ID).isEmpty());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        ArtifactDeletionReceipt receipt = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, receipt.state());
        assertEquals(0, receipt.attemptCount());
        assertEquals(0, ledger.pendingCount());
        assertCounter(registry, "completed", 1.0);
        assertCounter(registry, "failed", 0.0);
        assertEquals(0.0, registry.get("clearfolio.artifact.deletion.pending").gauge().value());
    }

    @Test
    void crossTenantDeletionDoesNotTouchArtifactOrReceiptStores() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        when(repository.findByTenantAndId(OTHER_TENANT_ID, JOB_ID)).thenReturn(Optional.empty());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(registry, receiptStore);
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                receiptStore,
                metrics,
                100
        );

        assertFalse(coordinator.deleteForTenant(JOB_ID, OTHER_TENANT_ID));

        verify(repository, never()).deleteByTenantAndId(OTHER_TENANT_ID, JOB_ID);
        verifyNoInteractions(artifactStore);
        verify(receiptStore, never()).request(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void artifactReadFailureHappensBeforeReceiptAndMetadataMutation() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        ArtifactStore artifactStore = new ArtifactStore() {
            @Override
            public void putPdf(UUID docId, byte[] pdfBytes) {
                throw new AssertionError("not used");
            }

            @Override
            public Optional<byte[]> getPdf(UUID docId) {
                throw new IllegalStateException("storage path must not escape");
            }

            @Override
            public void deletePdf(UUID docId) {
                throw new AssertionError("not used");
            }
        };
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
        );

        assertEquals("storage path must not escape", exception.getMessage());
        assertTrue(repository.findById(JOB_ID).isPresent());
        assertTrue(ledger.findByJobId(JOB_ID).isEmpty());
    }

    @Test
    void deleteFailureRemainsDurableAndCompletesOnRetry() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        FailingOnceArtifactStore artifactStore = new FailingOnceArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                registry,
                100
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt failed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, failed.state());
        assertEquals("artifact_store_delete_failed", failed.failureCode());
        assertEquals(1, failed.attemptCount());
        assertTrue(artifactStore.getPdf(JOB_ID).isPresent());
        assertCounter(registry, "failed", 1.0);

        assertEquals(1, coordinator.retryPendingWork());

        ArtifactDeletionReceipt completed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completed.state());
        assertEquals(1, completed.attemptCount());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        assertCounter(registry, "completed", 1.0);
    }

    @Test
    void fileBackedFailedReceiptCompletesAfterProcessRestart() {
        Path ledgerPath = tempDirectory.resolve("artifact_deletion_receipt.log");
        InMemoryConversionJobRepository repository = repositoryWithJob();
        FailingOnceArtifactStore artifactStore = new FailingOnceArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger firstLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionCoordinator firstProcess = coordinator(
                repository,
                artifactStore,
                firstLedger,
                new SimpleMeterRegistry(),
                100
        );

        assertTrue(firstProcess.deleteForTenant(JOB_ID, TENANT_ID));
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                firstLedger.findByJobId(JOB_ID).orElseThrow().state()
        );

        ArtifactDeletionLedger restartedLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionCoordinator restartedProcess = coordinator(
                repository,
                artifactStore,
                restartedLedger,
                new SimpleMeterRegistry(),
                100
        );

        restartedProcess.recoverPendingAfterStartup();

        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                restartedLedger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
    }

    @Test
    void requestedReceiptRecoversWhenMetadataWasAlreadyTombstoned() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.randomUUID(),
                TENANT_ID,
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                "cleanup-v1:" + UUID.randomUUID(),
                Instant.parse("2026-08-06T00:00:00Z")
        );
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertEquals(1, coordinator.retryPendingWork());

        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
    }

    @Test
    void digestMismatchFailsClosedWithoutDeletingReplacementBytes() {
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ConversionJobRepository repository = repositoryThatReplacesArtifactOnDelete(artifactStore, REPLACEMENT_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt failed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, failed.state());
        assertEquals("artifact_checksum_mismatch", failed.failureCode());
        assertTrue(java.util.Arrays.equals(REPLACEMENT_ARTIFACT, artifactStore.getPdf(JOB_ID).orElseThrow()));
    }

    @Test
    void absenceSentinelDeletesLateArtifactForPermanentlyReservedJobId() {
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        ConversionJobRepository repository = repositoryThatReplacesArtifactOnDelete(artifactStore, REPLACEMENT_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt completed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM, completed.artifactChecksum());
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, completed.state());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
    }

    @Test
    void recoveryBatchIsBoundedAndScheduledEntryPointUsesSamePath() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        UUID firstJobId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondJobId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        requestTombstoned(ledger, firstJobId, Instant.parse("2026-08-06T00:00:00Z"));
        requestTombstoned(ledger, secondJobId, Instant.parse("2026-08-06T00:00:10Z"));
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                1
        );

        assertEquals(1, coordinator.retryPendingWork());
        assertEquals(1, ledger.pendingCount());

        coordinator.retryPendingAfterDelay();

        assertEquals(0, ledger.pendingCount());
    }

    @Test
    void invalidRecoveryBatchSizeFailsFast() {
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(registry, receiptStore);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactDeletionCoordinator(
                        mock(ConversionJobRepository.class),
                        mock(ArtifactStore.class),
                        receiptStore,
                        metrics,
                        0
                )
        );

        assertEquals("maxReceiptsPerRun must be positive", exception.getMessage());
    }

    @Test
    void metadataTombstoneFailureLeavesRequestedReceiptForRecovery() {
        ConversionJob job = job();
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(false);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
        );

        assertEquals("tenant-scoped metadata tombstone was not applied", exception.getMessage());
        assertEquals(
                ArtifactDeletionState.DELETION_REQUESTED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(JOB_ID).isPresent());
    }

    @Test
    void globalCompatibilityDeletionUsesReceiptWhenMetadataExistsAndNoopsWhenMissing() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        coordinator.deleteGlobally(JOB_ID);
        coordinator.deleteGlobally(UUID.randomUUID());

        assertTrue(repository.findById(JOB_ID).isEmpty());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
    }

    private static ArtifactDeletionCoordinator coordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            SimpleMeterRegistry registry,
            int maxReceiptsPerRun
    ) {
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(registry, receiptStore);
        return new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                receiptStore,
                metrics,
                maxReceiptsPerRun
        );
    }

    private static InMemoryConversionJobRepository repositoryWithJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(job());
        return repository;
    }

    private static ConversionJob job() {
        return new ConversionJob(
                JOB_ID,
                TENANT_ID,
                "subject-north",
                "report.pdf",
                "application/pdf",
                "job-content-hash",
                ORIGINAL_ARTIFACT.length,
                3
        );
    }

    private static ConversionJobRepository repositoryThatReplacesArtifactOnDelete(
            InMemoryArtifactStore artifactStore,
            byte[] replacement
    ) {
        ConversionJob job = job();
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenAnswer(invocation -> {
            artifactStore.putPdf(JOB_ID, replacement);
            return true;
        });
        return repository;
    }

    private static void requestTombstoned(ArtifactDeletionLedger ledger, UUID jobId, Instant requestedAt) {
        ledger.request(
                UUID.randomUUID(),
                TENANT_ID,
                jobId,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:" + UUID.randomUUID(),
                requestedAt
        );
        ledger.markMetadataTombstoned(jobId, requestedAt.plusSeconds(1));
    }

    private static void assertCounter(SimpleMeterRegistry registry, String outcome, double expected) {
        assertEquals(
                expected,
                registry.get("clearfolio.artifact.deletion.attempts")
                        .tag("outcome", outcome)
                        .counter()
                        .count()
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FailingOnceArtifactStore implements ArtifactStore {
        private final InMemoryArtifactStore delegate = new InMemoryArtifactStore();
        private final AtomicBoolean failNextDelete = new AtomicBoolean(true);

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            delegate.putPdf(docId, pdfBytes);
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            return delegate.getPdf(docId);
        }

        @Override
        public void deletePdf(UUID docId) {
            if (failNextDelete.compareAndSet(true, false)) {
                throw new IllegalStateException("secret storage path");
            }
            delegate.deletePdf(docId);
        }
    }
}
