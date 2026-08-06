package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Covers defensive branches in the durable artifact-cleanup coordinator.
 */
class ArtifactDeletionCoordinatorCoverageTest {

    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "tenant-north";
    private static final byte[] ORIGINAL_ARTIFACT = "%PDF-1.7\noriginal".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT_ARTIFACT = "%PDF-1.7\nreplacement".getBytes(StandardCharsets.UTF_8);

    @Test
    void completedIdenticalReceiptIsIdempotentAndDoesNotRepeatMutation() {
        ConversionJob job = job();
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(true);
        when(artifactStore.getPdf(JOB_ID)).thenReturn(Optional.of(ORIGINAL_ARTIFACT));
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));
        ArtifactDeletionReceipt first = ledger.findByJobId(JOB_ID).orElseThrow();
        assertTrue(first.isCompleted());

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));
        ArtifactDeletionReceipt repeated = ledger.findByJobId(JOB_ID).orElseThrow();

        assertSame(first, repeated);
        verify(repository, times(1)).deleteByTenantAndId(TENANT_ID, JOB_ID);
        verify(artifactStore, times(1)).deletePdf(JOB_ID);
    }

    @Test
    void existingReceiptRejectsDifferentTenantBeforeChecksumComparison() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(artifactStore.getPdf(JOB_ID)).thenReturn(Optional.of(ORIGINAL_ARTIFACT));
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.randomUUID(),
                "tenant-south",
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                "cleanup-v1:tenant-conflict",
                Instant.parse("2026-08-06T00:00:00Z")
        );
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

        assertEquals("artifact deletion receipt conflicts with the active lifecycle", exception.getMessage());
    }

    @Test
    void existingReceiptRejectsDifferentChecksumForSameTenant() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(artifactStore.getPdf(JOB_ID)).thenReturn(Optional.of(REPLACEMENT_ARTIFACT));
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ledger.request(
                UUID.randomUUID(),
                TENANT_ID,
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                "cleanup-v1:checksum-conflict",
                Instant.parse("2026-08-06T00:00:00Z")
        );
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
        );
    }

    @Test
    void cleanupReadFailureUsesControlledCodeAndCanBeRetried() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(repository.deleteByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(true);
        ReadFailsOnceAfterSnapshotArtifactStore artifactStore = new ReadFailsOnceAfterSnapshotArtifactStore();
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, ledger, registry, 100);

        assertTrue(coordinator.deleteForTenant(JOB_ID, TENANT_ID));

        ArtifactDeletionReceipt failed = ledger.findByJobId(JOB_ID).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, failed.state());
        assertEquals("artifact_store_read_failed", failed.failureCode());
        assertEquals(1.0, registry.get("clearfolio.artifact.deletion.attempts")
                .tag("outcome", "failed").counter().count());

        assertEquals(1, coordinator.retryPendingWork());
        assertTrue(ledger.findByJobId(JOB_ID).orElseThrow().isCompleted());
    }

    @Test
    void unexpectedRecoveryFailureIsIsolatedAndCounted() {
        ArtifactDeletionReceipt pending = requestedReceipt();
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        when(receiptStore.pendingReceipts()).thenReturn(List.of(pending));
        when(receiptStore.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = coordinator(
                mock(ConversionJobRepository.class),
                mock(ArtifactStore.class),
                receiptStore,
                registry,
                100
        );

        assertEquals(1, coordinator.retryPendingWork());
        assertEquals(1.0, registry.get("clearfolio.artifact.deletion.attempts")
                .tag("outcome", "failed").counter().count());
    }

    @Test
    void cleanupMetricsContainOnlyFixedOutcomeTags() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionMetrics metrics = new ArtifactDeletionMetrics(registry, ledger);

        metrics.recordCompleted();
        metrics.recordFailed();

        for (Meter meter : registry.getMeters()) {
            Set<String> tagKeys = meter.getId().getTags().stream()
                    .map(Tag::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertTrue(tagKeys.isEmpty() || tagKeys.equals(Set.of("outcome")));
        }
    }

    @Test
    void sha256ProviderFailureDoesNotCreateDeletionEvidence() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(repository.findByTenantAndId(TENANT_ID, JOB_ID)).thenReturn(Optional.of(job()));
        when(artifactStore.getPdf(JOB_ID)).thenReturn(Optional.of(ORIGINAL_ARTIFACT));
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                repository,
                artifactStore,
                ledger,
                new SimpleMeterRegistry(),
                100
        );
        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> coordinator.deleteForTenant(JOB_ID, TENANT_ID)
            );
            assertEquals("SHA-256 digest unavailable", exception.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }

        assertTrue(ledger.findByJobId(JOB_ID).isEmpty());
    }

    @Test
    void nullCandidateAndMissingReceiptFailClosed() {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(
                mock(ConversionJobRepository.class),
                mock(ArtifactStore.class),
                ledger,
                new SimpleMeterRegistry(),
                100
        );

        assertThrows(NullPointerException.class, () -> coordinator.resumeReceipt(null));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.resumeReceipt(requestedReceipt())
        );
    }

    private static ArtifactDeletionCoordinator coordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore,
            SimpleMeterRegistry registry,
            int batchSize
    ) {
        return new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                receiptStore,
                new ArtifactDeletionMetrics(registry, receiptStore),
                batchSize
        );
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

    private static ArtifactDeletionReceipt requestedReceipt() {
        Instant requestedAt = Instant.parse("2026-08-06T00:00:00Z");
        return new ArtifactDeletionReceipt(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                TENANT_ID,
                JOB_ID,
                sha256(ORIGINAL_ARTIFACT),
                "cleanup-v1:coverage",
                requestedAt,
                requestedAt,
                ArtifactDeletionState.DELETION_REQUESTED,
                0,
                null,
                null,
                null
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

    private static final class ReadFailsOnceAfterSnapshotArtifactStore implements ArtifactStore {
        private final AtomicInteger reads = new AtomicInteger();
        private Optional<byte[]> artifact = Optional.of(ORIGINAL_ARTIFACT.clone());

        @Override
        public void putPdf(UUID docId, byte[] pdfBytes) {
            artifact = Optional.of(pdfBytes.clone());
        }

        @Override
        public Optional<byte[]> getPdf(UUID docId) {
            int currentRead = reads.incrementAndGet();
            if (currentRead == 2) {
                throw new IllegalStateException("private storage path");
            }
            return artifact.map(byte[]::clone);
        }

        @Override
        public void deletePdf(UUID docId) {
            artifact = Optional.empty();
        }
    }
}
