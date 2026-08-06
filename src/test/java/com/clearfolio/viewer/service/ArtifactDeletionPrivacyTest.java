package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
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
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;

/**
 * Verifies that best-effort artifact deletion failures do not disclose raw
 * conversion identifiers or exception-controlled storage details in logs.
 */
class ArtifactDeletionPrivacyTest {

    @Test
    void artifactDeletionFailureLogsOnlyAControlledMessage() {
        UUID jobId = UUID.randomUUID();
        String sensitiveFailure = "failed path /private/artifacts/" + jobId + ".pdf";
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJobStateStore stateStore = mock(ConversionJobStateStore.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(repository.deleteByTenantAndId("tenant-north", jobId)).thenReturn(true);
        doThrow(new IllegalStateException(sensitiveFailure))
                .when(artifactStore)
                .deletePdf(jobId);
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                stateStore,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                artifactStore,
                new ConversionProperties()
        );
        TenantContext tenantContext = new TenantContext(
                "tenant-north",
                "subject-north",
                Set.of("admin:write")
        );
        Logger logger = (Logger) LogManager.getLogger(DefaultDocumentConversionService.class);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);

        try {
            assertTrue(service.deleteJob(jobId, tenantContext));
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        assertEquals(1, appender.events().size());
        LogEvent event = appender.events().getFirst();
        String message = event.getMessage().getFormattedMessage();
        assertTrue(message.contains("Artifact deletion failed"));
        assertTrue(!message.contains(jobId.toString()));
        assertTrue(!message.contains(sensitiveFailure));
        assertNull(event.getThrown());
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
