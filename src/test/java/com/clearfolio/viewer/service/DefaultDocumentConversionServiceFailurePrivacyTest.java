package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Verifies that artifact-cleanup failures do not expose provider-controlled
 * diagnostics or raw job identifiers through warning logs.
 */
class DefaultDocumentConversionServiceFailurePrivacyTest {

    @Test
    void artifactDeletionFailureLogExcludesRawProviderDataAndJobIdentifier() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionWorker worker = mock(ConversionWorker.class);
        DocumentValidationService validationService = mock(DocumentValidationService.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                validationService,
                worker,
                artifactStore,
                new ConversionProperties()
        );
        UUID jobId = UUID.fromString("4ce471f3-2f79-4b82-879a-a36ce43e48fc");
        String providerMessage = "customer@example.com /tenant/private/report.pdf";
        org.mockito.Mockito.doThrow(new IllegalStateException(providerMessage))
                .when(artifactStore)
                .deletePdf(jobId);
        CapturingAppender appender = attachAppender();

        try {
            service.deleteJob(jobId);
        } finally {
            appender.closeAndDetach();
        }

        String renderedLog = appender.renderedLog();
        assertFalse(renderedLog.contains(providerMessage));
        assertFalse(renderedLog.contains("customer@example.com"));
        assertFalse(renderedLog.contains("/tenant/private/report.pdf"));
        assertFalse(renderedLog.contains(jobId.toString()));
    }

    private static CapturingAppender attachAppender() {
        Logger logger = (Logger) LogManager.getLogger(DefaultDocumentConversionService.class);
        CapturingAppender appender = new CapturingAppender(logger);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        return appender;
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final Logger logger;
        private final List<String> renderedEvents = new ArrayList<>();

        private CapturingAppender(Logger logger) {
            super(
                    "conversion-service-failure-privacy-test",
                    null,
                    PatternLayout.newBuilder().withPattern("%m%throwable").build(),
                    false,
                    null
            );
            this.logger = logger;
        }

        @Override
        public void append(LogEvent event) {
            renderedEvents.add(getLayout().toSerializable(event).toString());
        }

        private String renderedLog() {
            return String.join("\n", renderedEvents);
        }

        private void closeAndDetach() {
            logger.removeAppender(this);
            stop();
        }
    }
}
