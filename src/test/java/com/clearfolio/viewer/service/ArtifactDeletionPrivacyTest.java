package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionCoordinator;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionLedger;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionMetrics;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionReceipt;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionState;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Verifies that durable artifact-deletion failures retain only controlled,
 * privacy-safe evidence and never disclose storage-controlled details.
 */
class ArtifactDeletionPrivacyTest {

    @Test
    void artifactDeletionFailurePersistsControlledEvidenceWithoutSensitiveDetails() {
        UUID jobId = UUID.randomUUID();
        String sensitiveFailure = "failed path /private/artifacts/" + jobId + ".pdf";
        String tenantId = "tenant-north";
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJobStateStore stateStore = mock(ConversionJobStateStore.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        ConversionJob job = new ConversionJob(
                jobId,
                tenantId,
                "subject-north",
                "private-report.pdf",
                "application/pdf",
                "privacy-delete-hash",
                42L,
                3
        );
        when(repository.findByTenantAndId(tenantId, jobId)).thenReturn(Optional.of(job));
        when(repository.deleteByTenantAndId(tenantId, jobId)).thenReturn(true);
        when(artifactStore.getPdf(jobId)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException(sensitiveFailure))
                .when(artifactStore)
                .deletePdf(jobId);
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactDeletionCoordinator coordinator = new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                ledger,
                new ArtifactDeletionMetrics(registry, ledger),
                100
        );
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                stateStore,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                artifactStore,
                new ConversionProperties(),
                coordinator
        );
        TenantContext tenantContext = new TenantContext(
                tenantId,
                "subject-north",
                Set.of("admin:write")
        );
        Logger logger = (Logger) LogManager.getLogger(ArtifactDeletionCoordinator.class);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);

        try {
            assertTrue(service.deleteJob(jobId, tenantContext));
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        ArtifactDeletionReceipt receipt = ledger.findByJobId(jobId).orElseThrow();
        assertEquals(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, receipt.state());
        assertEquals("artifact_store_delete_failed", receipt.failureCode());
        assertEquals(1, receipt.attemptCount());
        assertTrue(!receipt.failureCode().contains(jobId.toString()));
        assertTrue(!receipt.failureCode().contains(sensitiveFailure));
        assertEquals(1.0, registry.get("clearfolio.artifact.deletion.attempts")
                .tag("outcome", "failed")
                .counter()
                .count());
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> {
            assertTrue(Set.of("outcome").contains(tag.getKey()));
            assertTrue(Set.of("completed", "failed").contains(tag.getValue()));
            assertTrue(!tag.getValue().contains(jobId.toString()));
            assertTrue(!tag.getValue().contains(sensitiveFailure));
        }));
        assertTrue(appender.events().stream().allMatch(event -> {
            String message = event.getMessage().getFormattedMessage();
            return !message.contains(jobId.toString())
                    && !message.contains(sensitiveFailure)
                    && event.getThrown() == null;
        }));
    }

    /**
     * Minimal Log4j2 appender that retains immutable events for assertions.
     */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        private CapturingAppender() {
            super(
                    "artifact-deletion-privacy",
                    null,
                    PatternLayout.createDefaultLayout(),
                    false,
                    Property.EMPTY_ARRAY
            );
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private List<LogEvent> events() {
            return List.copyOf(events);
        }
    }
}
