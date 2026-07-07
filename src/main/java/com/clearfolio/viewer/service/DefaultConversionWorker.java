package com.clearfolio.viewer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.PdfArtifactGenerator;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.RepositoryBackedConversionJobStateStore;

/**
 * Default background worker that executes conversion jobs with retry backoff.
 */
@Component
public class DefaultConversionWorker implements ConversionWorker {

    private static final long MIN_INITIAL_RETRY_DELAY_MS = 250L;

    private final ConversionJobRepository repository;
    private final ConversionJobStateStore stateStore;
    private final Executor conversionExecutor;
    private final ArtifactStore artifactStore;
    private final PdfArtifactGenerator pdfArtifactGenerator;
    private final long retryInitialDelayMs;
    private final long retryMaxDelayMs;
    private final double retryBackoffMultiplier;
    private final Function<UUID, String> conversionTask;

    /**
     * Creates a conversion worker using the default conversion task implementation.
     *
     * @param repository conversion job repository
     * @param stateStore conversion job lifecycle state store
     * @param conversionExecutor asynchronous conversion executor
     * @param conversionProperties conversion configuration values
     */
    @Autowired
    public DefaultConversionWorker(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            Executor conversionExecutor,
            ArtifactStore artifactStore,
            PdfArtifactGenerator pdfArtifactGenerator,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties) {
        this(
                repository,
                stateStore,
                conversionExecutor,
                artifactStore,
                pdfArtifactGenerator,
                conversionProperties,
                null
        );
    }

    DefaultConversionWorker(
            ConversionJobRepository repository,
            Executor conversionExecutor,
            ArtifactStore artifactStore,
            PdfArtifactGenerator pdfArtifactGenerator,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties) {
        this(
                repository,
                stateStoreFrom(repository),
                conversionExecutor,
                artifactStore,
                pdfArtifactGenerator,
                conversionProperties,
                null
        );
    }

    DefaultConversionWorker(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            Executor conversionExecutor,
            ArtifactStore artifactStore,
            PdfArtifactGenerator pdfArtifactGenerator,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties,
            Function<UUID, String> conversionTask) {
        this.repository = repository;
        this.stateStore = stateStore;
        this.conversionExecutor = conversionExecutor;
        this.artifactStore = artifactStore;
        this.pdfArtifactGenerator = pdfArtifactGenerator;
        this.retryInitialDelayMs = Math.max(
                MIN_INITIAL_RETRY_DELAY_MS,
                conversionProperties.getRetryInitialDelayMs()
        );
        this.retryMaxDelayMs = Math.max(
                retryInitialDelayMs,
                conversionProperties.getRetryMaxDelayMs()
        );
        this.retryBackoffMultiplier = conversionProperties.getRetryBackoffMultiplier();
        this.conversionTask = conversionTask == null ? this::performDefaultConversion : conversionTask;
    }

    DefaultConversionWorker(
            ConversionJobRepository repository,
            Executor conversionExecutor,
            ArtifactStore artifactStore,
            PdfArtifactGenerator pdfArtifactGenerator,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties,
            Function<UUID, String> conversionTask) {
        this(
                repository,
                stateStoreFrom(repository),
                conversionExecutor,
                artifactStore,
                pdfArtifactGenerator,
                conversionProperties,
                conversionTask
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enqueue(UUID jobId) {
        try {
            CompletableFuture.runAsync(() -> process(jobId), conversionExecutor);
        } catch (RejectedExecutionException ex) {
            markDeadLetteredForQueueSaturation(jobId);
        }
    }

    private void process(UUID jobId) {
        Instant now = Instant.now();
        repository.findById(jobId).ifPresent(job -> {
            if (!job.isReadyForProcessing(now)) {
                Instant retryAt = job.getRetryAt();
                if (retryAt != null && now.isBefore(retryAt)) {
                    scheduleRetry(jobId, retryAt);
                }
                return;
            }

            java.util.Optional<ConversionJob> claimed = stateStore.claimForProcessing(jobId, now);
            if (claimed.isEmpty()) {
                return;
            }

            try {
                String convertedResourcePath = conversionTask.apply(jobId);
                stateStore.markSucceeded(jobId, convertedResourcePath, "conversion completed");
            } catch (Throwable ex) {
                onFailure(claimed.get(), failureReason(ex));
                if (ex instanceof VirtualMachineError error) {
                    throw error;
                }
            }
        });
    }

    private String failureReason(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "conversion failed: " + error.getClass().getSimpleName();
        }
        return "conversion failed: " + message;
    }

    private void onFailure(ConversionJob job, String reason) {
        if (job.canRetry()) {
            long retryDelayMs = computeRetryDelay(job.getAttemptCount());
            Instant retryAt = Instant.now().plusMillis(retryDelayMs);
            stateStore.scheduleRetry(job.getJobId(), "retry scheduled in " + retryDelayMs + "ms", retryAt);
            scheduleRetry(job.getJobId(), retryAt);
            return;
        }

        stateStore.markDeadLettered(job.getJobId(), reason);
    }

    private long computeRetryDelay(int attemptCount) {
        if (attemptCount <= 1) {
            return retryInitialDelayMs;
        }

        double power = Math.pow(retryBackoffMultiplier, Math.max(0, attemptCount - 1));
        long exponential = Math.round(retryInitialDelayMs * power);
        long bounded = Math.max(retryInitialDelayMs, exponential);
        return Math.min(bounded, retryMaxDelayMs);
    }

    private void scheduleRetry(UUID jobId, Instant retryAt) {
        long delayMs = Duration.between(Instant.now(), retryAt).toMillis();
        if (delayMs <= 0) {
            enqueue(jobId);
            return;
        }

        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    try {
                        conversionExecutor.execute(() -> process(jobId));
                    } catch (RejectedExecutionException ex) {
                        markDeadLetteredForQueueSaturation(jobId);
                    }
                });
    }

    private void markDeadLetteredForQueueSaturation(UUID jobId) {
        stateStore.markDeadLettered(jobId, "worker queue saturated");
    }

    private String performDefaultConversion(UUID jobId) {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("conversion interrupted");
        }

        ConversionJob job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("job not found"));

        byte[] pdfBytes = pdfArtifactGenerator.generatePdf(job);
        artifactStore.putPdf(jobId, pdfBytes);
        return "/artifacts/" + jobId + ".pdf";
    }

    private static ConversionJobStateStore stateStoreFrom(ConversionJobRepository repository) {
        if (repository instanceof ConversionJobStateStore conversionJobStateStore) {
            return conversionJobStateStore;
        }

        return new RepositoryBackedConversionJobStateStore(repository);
    }
}
