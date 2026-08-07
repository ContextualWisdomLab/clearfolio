package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Verifies that durable-deletion operational warnings expose only controlled
 * event text and never exception-selected implementation details.
 */
class ArtifactDeletionCoordinatorPrivacyTest {

    private static final String TENANT_ID = "tenant-private-warning";
    private static final UUID SNAPSHOT_JOB_ID =
            UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID RECOVERY_JOB_ID =
            UUID.fromString("52000000-0000-0000-0000-000000000001");

    @Test
    void operationalWarningsExcludeExceptionSelectedClassNames() {
        CapturingAppender appender = attachAppender();
        try {
            triggerPreSnapshotReadFailure();
            triggerRecoveryFailure();
        } finally {
            appender.closeAndDetach();
        }

        assertEquals(
                List.of(
                        "Artifact deletion retained a pre-snapshot receipt.",
                        "Artifact deletion recovery retained an incomplete receipt."
                ),
                appender.messages()
        );
        assertFalse(
                appender.messages().stream()
                        .anyMatch(message -> message.contains(SensitiveStorageImplementationException.class.getName()))
        );
    }

    private static void triggerPreSnapshotReadFailure() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJob job = mock(ConversionJob.class);
        when(repository.findByTenantAndId(TENANT_ID, SNAPSHOT_JOB_ID))
                .thenReturn(Optional.of(job));
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(artifactStore.getPdf(SNAPSHOT_JOB_ID))
                .thenThrow(new SensitiveStorageImplementationException());
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();

        coordinator(repository, artifactStore, ledger).deleteForTenant(SNAPSHOT_JOB_ID, TENANT_ID);
    }

    private static void triggerRecoveryFailure() {
        ArtifactDeletionReceipt receipt = mock(ArtifactDeletionReceipt.class);
        when(receipt.jobId()).thenReturn(RECOVERY_JOB_ID);
        ArtifactDeletionReceiptStore receiptStore = mock(ArtifactDeletionReceiptStore.class);
        when(receiptStore.pendingReceipts()).thenReturn(List.of(receipt));
        when(receiptStore.findByJobId(RECOVERY_JOB_ID))
                .thenThrow(new SensitiveStorageImplementationException());

        coordinator(
                mock(ConversionJobRepository.class),
                mock(ArtifactStore.class),
                receiptStore
        ).retryPendingWork();
    }

    private static ArtifactDeletionCoordinator coordinator(
            ConversionJobRepository repository,
            ArtifactStore artifactStore,
            ArtifactDeletionReceiptStore receiptStore
    ) {
        return new ArtifactDeletionCoordinator(
                repository,
                artifactStore,
                receiptStore,
                new ArtifactDeletionMetrics(receiptStore),
                100
        );
    }

    private static CapturingAppender attachAppender() {
        Logger logger = (Logger) LogManager.getLogger(ArtifactDeletionCoordinator.class);
        CapturingAppender appender = new CapturingAppender(logger, logger.getLevel());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        return appender;
    }

    private static final class SensitiveStorageImplementationException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final Logger logger;
        private final Level originalLevel;
        private final List<String> messages = new ArrayList<>();

        private CapturingAppender(Logger logger, Level originalLevel) {
            super(
                    "artifact-deletion-privacy-test-appender",
                    null,
                    PatternLayout.newBuilder().withPattern("%m").build(),
                    false,
                    null
            );
            this.logger = logger;
            this.originalLevel = originalLevel;
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }

        private void closeAndDetach() {
            logger.removeAppender(this);
            logger.setLevel(originalLevel);
            stop();
        }
    }
}
