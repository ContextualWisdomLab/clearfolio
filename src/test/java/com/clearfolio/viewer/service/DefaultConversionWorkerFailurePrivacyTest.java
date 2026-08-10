package com.clearfolio.viewer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.artifact.PdfBoxArtifactGenerator;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

/**
 * Regression tests for the public conversion-failure status privacy boundary.
 */
class DefaultConversionWorkerFailurePrivacyTest {

    /**
     * Prevents exception-controlled paths, document values, or other sensitive data from becoming
     * the client-visible conversion status message.
     */
    @Test
    void workerDoesNotExposeExceptionMessageInJobStatus() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setMaxRetryAttempts(1);

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "confidential-report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "source-digest",
                64L,
                1
        );
        repository.save(job);

        String sensitiveProviderMessage =
                "converter failed at /srv/tenants/acme/private/confidential-report.docx for customer@example.test";
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                new InMemoryArtifactStore(),
                new PdfBoxArtifactGenerator(),
                conversionProperties,
                id -> {
                    throw new IllegalStateException(sensitiveProviderMessage);
                }
        );

        worker.enqueue(jobId);

        assertThat(job.getStatus()).isEqualTo(ConversionJobStatus.FAILED);
        assertThat(job.isDeadLettered()).isTrue();
        assertThat(job.getStatusMessage()).isEqualTo("conversion failed: IllegalStateException");
        assertThat(job.getStatusMessage()).doesNotContain("/srv/tenants", "customer@example.test", "confidential-report.docx");
    }
}
