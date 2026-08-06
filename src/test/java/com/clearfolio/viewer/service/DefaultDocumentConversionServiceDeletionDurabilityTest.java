package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionCoordinator;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionLedger;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionMetrics;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionState;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Verifies that conversion-service deletion entry points use durable cleanup.
 */
class DefaultDocumentConversionServiceDeletionDurabilityTest {

    private static final String TENANT_ID = "tenant-north";
    private static final byte[] PDF_BYTES = "%PDF-1.7\nprivate".getBytes(StandardCharsets.UTF_8);

    @Test
    void tenantDeletionPersistsFailureAndCoordinatorRetryCompletesIt() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId));
        FailingOnceArtifactStore artifactStore = new FailingOnceArtifactStore();
        artifactStore.putPdf(jobId, PDF_BYTES);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, ledger);
        DefaultDocumentConversionService service = service(
                repository,
                artifactStore,
                coordinator
        );

        boolean deleted = service.deleteJob(
                jobId,
                new TenantContext(
                        TENANT_ID,
                        "subject-north",
                        Set.of(TenantPermissions.JOB_DELETE)
                )
        );

        assertTrue(deleted);
        assertTrue(repository.findById(jobId).isEmpty());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                ledger.findByJobId(jobId).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(jobId).isPresent());

        assertEquals(1, coordinator.retryPendingWork());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(jobId).orElseThrow().state()
        );
        assertTrue(artifactStore.getPdf(jobId).isEmpty());
    }

    @Test
    void globalCompatibilityDeletionUsesTheSameDurableCoordinator() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId));
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(jobId, PDF_BYTES);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        ArtifactDeletionCoordinator coordinator = coordinator(repository, artifactStore, ledger);
        DefaultDocumentConversionService service = service(
                repository,
                artifactStore,
                coordinator
        );

        service.deleteJob(jobId);

        assertTrue(repository.findById(jobId).isEmpty());
        assertTrue(artifactStore.getPdf(jobId).isEmpty());
        assertEquals(
                ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED,
                ledger.findByJobId(jobId).orElseThrow().state()
        );
    }

    private static DefaultDocumentConversionService service(
            InMemoryConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionCoordinator coordinator
    ) {
        return new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                    // Validation is unrelated to the deletion-only contract.
                },
                jobId -> {
                    // Conversion dispatch is unrelated to the deletion-only contract.
                },
                artifactStore,
                new ConversionProperties(),
                coordinator
        );
    }

    private static ArtifactDeletionCoordinator coordinator(
            InMemoryConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionLedger ledger
    ) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(registry, ledger),
                100
        );
    }

    private static ConversionJob job(UUID jobId) {
        return new ConversionJob(
                jobId,
                TENANT_ID,
                "subject-north",
                "report.pdf",
                "application/pdf",
                "delete-durability-hash-" + jobId,
                PDF_BYTES.length,
                3
        );
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
                throw new IllegalStateException("private storage path");
            }
            delegate.deletePdf(docId);
        }
    }
}
