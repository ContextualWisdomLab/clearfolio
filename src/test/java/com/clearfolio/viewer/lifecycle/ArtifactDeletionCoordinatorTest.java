package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

/**
 * Verifies receipt-first metadata tombstoning, exact-lifecycle cleanup, bounded
 * recovery, aggregate evidence, and restart behavior.
 */
@ResourceLock("java.security.Security.providers")
class ArtifactDeletionCoordinatorTest {

    private static final String TENANT_ID = "tenant-north";
    private static final String OTHER_TENANT_ID = "tenant-south";
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final byte[] ORIGINAL_ARTIFACT = "%PDF-1.7\noriginal".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT_ARTIFACT = "%PDF-1.7\nreplacement".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void authorizedDeletionCompletesReceiptAndAggregateEvidence() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(ledger);
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, ledger, metrics, 100);

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        assertTrue(repository.findById(JOB_ID).isEmpty());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        ArtifactDeletionReceipt receipt = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, receipt.state());
        assertEquals(1L, metrics.completedAttempts());
        assertEquals(0L, metrics.failedAttempts());
        assertEquals(0, metrics.pendingReceipts());
    }

    @Test
    void crossTenantAndMissingContextBoundariesDoNotTouchArtifactOrReceiptStores() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        when(repository.findByTenantAndId(OTHER_TENANT_ID, JOB_ID)).thenReturn(Optional.empty());
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                receiptStore,
                new ArtifactDeletionMetrics(receiptStore),
                100
        );

        assertFalse(coordinator.deleteForTenant(JOB_ID, OTHER_TENANT_ID));
        assertThrows(NullPointerException.class, () -> coordinator.deleteForTenant(null, TENANT_ID));
        assertThrows(NullPointerException.class, () -> coordinator.deleteForTenant(JOB_ID, null));

        verify(repository, never()).deleteByTenantAndId(OTHER_TENANT_ID, JOB_ID);
        verifyNoInteractions(artifactStore);
        verify(receiptStore, never()).request(any(), any(), any(), any(), any(), any());
    }

    @Test
    void artifactSnapshotFailurePrecedesReceiptAndMetadataMutation() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        ArtifactStore artifactStore = throwingStore(true, false);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger),
                100
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
        );

        assertEquals("artifact read failed", exception.getMessage());
        assertTrue(repository.findById(JOB_ID).isPresent());
        assertTrue(ledger.findByJobId(JOB_ID).isEmpty());
    }

    @Test
    void deleteFailureRemainsDurableAndCompletesOnRetry() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        FailingOnceArtifactStore artifactStore = new FailingOnceArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(ledger);
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, ledger, metrics, 100);

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt failed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, failed.state());
        assertEquals("artifact_store_delete_failed", failed.failureCode());
        assertEquals(1L, metrics.failedAttempts());
        assertEquals(1, metrics.pendingReceipts());

        assertEquals(1, coordinator.retryPendingWork());

        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        assertEquals(1L, metrics.completedAttempts());
        assertEquals(0, metrics.pendingReceipts());
    }

    @Test
    void fileBackedFailedReceiptCompletesAfterRestart() {
        Path ledgerPath = tempDirectory.resolve("artifact-deletion-receipts.log");
        InMemoryConversionJobRepository repository = repositoryWithJob();
        FailingOnceArtifactStore artifactStore = new FailingOnceArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger firstLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionCoordinator firstProcess = coordinator(
                repository,
                artifactStore,
                firstLedger,
                new ArtifactDeletionMetrics(firstLedger),
                100
        );
        assertTrue(firstProcess.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionLedger restartedLedger = new ArtifactDeletionLedger(ledgerPath);
        ArtifactDeletionCoordinator restartedProcess = coordinator(
                repository,
                artifactStore,
                restartedLedger,
                new ArtifactDeletionMetrics(restartedLedger),
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
    void requestedAndTombstonedReceiptsResumeWithoutRestoringMetadata() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger requestedLedger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt requested = request(
                requestedLedger,
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                Instant.parse("2026-08-06T00:00:00Z")
        );
        ArtifactDeletionCoordinator requestedCoordinator = coordinator(
                repository,
                artifactStore,
                requestedLedger,
                new ArtifactDeletionMetrics(requestedLedger),
                100
        );
        requestedCoordinator.resumeReceipt(requested);
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                requestedLedger.findByJobId(JOB_ID).orElseThrow().state()
        );

        UUID secondJobId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        ArtifactDeletionLedger tombstonedLedger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt tombstoned = request(
                tombstonedLedger,
                secondJobId,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                Instant.parse("2026-08-06T00:01:00Z")
        );
        tombstoned = tombstonedLedger.markMetadataTombstoned(secondJobId, tombstoned.requestedAt());
        ArtifactDeletionCoordinator tombstonedCoordinator = coordinator(
                repository,
                artifactStore,
                tombstonedLedger,
                new ArtifactDeletionMetrics(tombstonedLedger),
                100
        );
        tombstonedCoordinator.resumeReceipt(tombstoned);
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                tombstonedLedger.findByJobId(secondJobId).orElseThrow().state()
        );
    }

    @Test
    void digestMismatchRetainsReplacementWhileAbsenceSentinelDeletesLateBytes() {
        InMemoryArtifactStore mismatchStore = new InMemoryArtifactStore();
        mismatchStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ConversionJobRepository mismatchRepository = repositoryThatWritesOnDelete(
                mismatchStore,
                REPLACEMENT_ARTIFACT
        );
        ArtifactDeletionLedger mismatchLedger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator mismatchCoordinator = coordinator(
                mismatchRepository,
                mismatchStore,
                mismatchLedger,
                new ArtifactDeletionMetrics(mismatchLedger),
                100
        );

        assertTrue(mismatchCoordinator.deleteForTenant(JOB_ID, TENANT_ID));
        assertEquals(
                "artifact_checksum_mismatch",
                mismatchLedger.findByJobId(JOB_ID).orElseThrow().failureCode()
        );
        assertTrue(Arrays.equals(REPLACEMENT_ARTIFACT, mismatchStore.getPdf(JOB_ID).orElseThrow()));

        InMemoryArtifactStore lateStore = new InMemoryArtifactStore();
        ConversionJobRepository lateRepository = repositoryThatWritesOnDelete(lateStore, REPLACEMENT_ARTIFACT);
        ArtifactDeletionLedger lateLedger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator lateCoordinator = coordinator(
                lateRepository,
                lateStore,
                lateLedger,
                new ArtifactDeletionMetrics(lateLedger),
                100
        );
        assertTrue(lateCoordinator.deleteForTenant(JOB_ID, TENANT_ID));
        assertEquals(
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                lateLedger.findByJobId(JOB_ID).orElseThrow().artifactChecksum()
        );
        assertTrue(lateStore.getPdf(JOB_ID).isEmpty());
    }

    @Test
    void cleanupReadFailureAndNullStoreResultBecomeControlledRetryEvidence() {
        for (ArtifactStore store : List.of(throwingStore(true, false), nullReturningStore())) {
            ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
            ArtifactDeletionReceipt receipt = request(
                    ledger,
                    UUID.randomUUID(),
                    ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                    Instant.now()
            );
            receipt = ledger.markMetadataTombstoned(receipt.jobId(), receipt.requestedAt());
            ArtifactDeletionCoordinator coordinator = coordinator(
                    new InMemoryConversionJobRepository(),
                    store,
                    ledger,
                    new ArtifactDeletionMetrics(ledger),
                    100
            );

            coordinator.resumeReceipt(receipt);

            assertEquals(
                    "artifact_store_read_failed",
                    ledger.findByJobId(receipt.jobId()).orElseThrow().failureCode()
            );
        }
    }

    @Test
    void recoveryBatchIsBoundedIsolatesFailuresAndUsesScheduledEntryPoint() {
        ArtifactDeletionReceipt failedLookup = receiptSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
        ArtifactDeletionReceipt successful = receiptSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        ArtifactDeletionReceiptStore store = mock(ArtifactDeletionReceiptStore.class);
        when(store.pendingReceipts()).thenReturn(List.of(failedLookup, successful));
        when(store.findByJobId(failedLookup.jobId())).thenReturn(Optional.empty());
        when(store.findByJobId(successful.jobId())).thenReturn(Optional.of(successful));
        when(store.markCleanupCompleted(any(), any())).thenReturn(successful);
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(store);
        ArtifactDeletionCoordinator coordinator = coordinator(
                mock(ConversionJobRepository.class),
                new InMemoryArtifactStore(),
                store,
                metrics,
                2
        );

        assertEquals(2, coordinator.retryPendingWork());
        assertEquals(1L, metrics.failedAttempts());
        assertEquals(1L, metrics.completedAttempts());

        when(store.pendingReceipts()).thenReturn(List.of());
        coordinator.retryPendingAfterDelay();
        assertEquals(0, coordinator.retryPendingWork());
    }

    @Test
    void existingReceiptMustMatchTheActiveLifecycleAndCompletedReceiptIsNoop() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt existing = ledger.request(
                UUID.randomUUID(),
                OTHER_TENANT_ID,
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                "cleanup-v1:conflict",
                Instant.now()
        );
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger),
                100
        );

        assertThrows(IllegalStateException.class, () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID));
        assertSame(existing, ledger.findByJobId(JOB_ID).orElseThrow());

        ArtifactDeletionLedger completedLedger = new ArtifactDeletionLedger();
        ArtifactDeletionReceipt completed = request(
                completedLedger,
                UUID.randomUUID(),
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                Instant.now()
        );
        completed = completedLedger.markMetadataTombstoned(completed.jobId(), completed.requestedAt());
        completed = completedLedger.markCleanupPending(completed.jobId(), completed.stateChangedAt());
        completed = completedLedger.markCleanupCompleted(completed.jobId(), completed.stateChangedAt());
        ArtifactDeletionMetrics completedMetrics = new ArtifactDeletionMetrics(completedLedger);
        coordinator(
                new InMemoryConversionJobRepository(),
                new InMemoryArtifactStore(),
                completedLedger,
                completedMetrics,
                100
        ).resumeReceipt(completed);
        assertEquals(0L, completedMetrics.completedAttempts());
    }

    @Test
    void globalCompatibilityPathUsesReceiptsForKnownJobsAndDeletesMissingMetadataIdempotently() {
        InMemoryConversionJobRepository repository = repositoryWithJob();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(ledger),
                100
        );

        coordinator.deleteGlobally(JOB_ID);
        UUID missing = UUID.randomUUID();
        coordinator.deleteGlobally(missing);

        assertTrue(repository.findById(JOB_ID).isEmpty());
        assertTrue(artifactStore.getPdf(JOB_ID).isEmpty());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(JOB_ID).orElseThrow().state()
        );
        assertTrue(ledger.findByJobId(missing).isEmpty());
        assertThrows(NullPointerException.class, () -> coordinator.deleteGlobally(null));
    }

    @Test
    void constructorAndMissingReceiptFailClosed() {
        ArtifactDeletionReceiptStore store = mock(ArtifactDeletionReceiptStore.class);
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(store);
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);

        assertThrows(NullPointerException.class, () -> new ArtifactDeletionMetrics(null));
        assertThrows(NullPointerException.class, () -> coordinator(null, artifactStore, store, metrics, 1));
        assertThrows(NullPointerException.class, () -> coordinator(repository, null, store, metrics, 1));
        assertThrows(NullPointerException.class, () -> coordinator(repository, artifactStore, null, metrics, 1));
        assertThrows(NullPointerException.class, () -> coordinator(repository, artifactStore, store, null, 1));
        assertThrows(IllegalArgumentException.class, () -> coordinator(repository, artifactStore, store, metrics, 0));

        ArtifactDeletionReceipt candidate = receiptSnapshot(JOB_ID);
        when(store.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, store, metrics, 1);
        assertThrows(IllegalStateException.class, () -> coordinator.resumeReceipt(candidate));
        assertThrows(NullPointerException.class, () -> coordinator.resumeReceipt(null));
    }

    @Test
    void sha256ProviderAbsenceFailsBeforeReceiptCreation() {
        synchronized (SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> providers = SecurityProviderTestSupport.sha256ProviderPositions();
            try {
                for (ProviderPosition provider : providers) {
                    Security.removeProvider(provider.provider().getName());
                }
                InMemoryConversionJobRepository repository = repositoryWithJob();
                InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
                artifactStore.putPdf(JOB_ID, ORIGINAL_ARTIFACT);
                ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
                ArtifactDeletionCoordinator coordinator = coordinator(
                        repository,
                        artifactStore,
                        ledger,
                        new ArtifactDeletionMetrics(ledger),
                        100
                );

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
                );
                assertEquals("SHA-256 digest unavailable", exception.getMessage());
                assertTrue(ledger.findByJobId(JOB_ID).isEmpty());
            } finally {
                for (ProviderPosition provider : providers) {
                    Security.insertProviderAt(provider.provider(), provider.position());
                }
            }
        }
    }

    private static ArtifactDeletionCoordinator coordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            ArtifactDeletionMetrics metrics,
            int maxReceiptsPerRun
    ) {
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

    private static ArtifactDeletionReceipt request(
            ArtifactDeletionLedger ledger,
            UUID jobId,
            String checksum,
            Instant requestedAt
    ) {
        return ledger.request(
                UUID.randomUUID(),
                TENANT_ID,
                jobId,
                checksum,
                "cleanup-v1:" + jobId.toString().replace("-", ""),
                requestedAt
        );
    }

    private static ArtifactDeletionReceipt receiptSnapshot(UUID jobId) {
        Instant requestedAt = Instant.parse("2026-08-06T00:00:00Z");
        return new ArtifactDeletionReceipt(
                UUID.randomUUID(),
                TENANT_ID,
                jobId,
                ArtifactDeletionCoordinator.ABSENT_ARTIFACT_CHECKSUM,
                "cleanup-v1:snapshot",
                requestedAt,
                requestedAt,
                ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING,
                0,
                null,
                null,
                null
        );
    }

    private static ConversionJobRepository repositoryThatWritesOnDelete(
            InMemoryArtifactStore artifactStore,
            byte[] replacement
    ) {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenAnswer(invocation -> {
            artifactStore.putPdf(JOB_ID, replacement);
            return true;
        });
        return repository;
    }

    private static ArtifactStore throwingStore(boolean failRead, boolean failDelete) {
        return new ArtifactStore() {
            @Override
            public void putPdf(UUID docId, byte[] pdfBytes) {
                throw new AssertionError("not used");
            }

            @Override
            public Optional<byte[]> getPdf(UUID docId) {
                if (failRead) {
                    throw new IllegalStateException("artifact read failed");
                }
                return Optional.empty();
            }

            @Override
            public void deletePdf(UUID docId) {
                if (failDelete) {
                    throw new IllegalStateException("artifact delete failed");
                }
            }
        };
    }

    private static ArtifactStore nullReturningStore() {
        return new ArtifactStore() {
            @Override
            public void putPdf(UUID docId, byte[] pdfBytes) {
            }

            @Override
            public Optional<byte[]> getPdf(UUID docId) {
                return null;
            }

            @Override
            public void deletePdf(UUID docId) {
            }
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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
                throw new IllegalStateException("artifact delete failed");
            }
            delegate.deletePdf(docId);
        }
    }
}
