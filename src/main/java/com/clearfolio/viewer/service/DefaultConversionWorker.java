package com.clearfolio.viewer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.PdfArtifactGenerator;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.RepositoryBackedConversionJobStateStore;

/**
 * Default background worker that executes conversion jobs with retry backoff.
 *
 * <p>PDF uploads are served passthrough: the original bytes seeded into the
 * artifact store at submit time become the artifact unchanged. Non-PDF sources
 * still produce a placeholder preview PDF because real document conversion
 * (docx, hwp, and similar formats) remains future work.
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
    private final long processingLeaseTimeoutMs;
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
        this.processingLeaseTimeoutMs = conversionProperties.getProcessingLeaseTimeoutMs();
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

    /**
     * Re-enqueues recoverable jobs after the application starts.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingJobsAfterStartup() {
        recoverPendingJobsOnStartup();
    }

    /**
     * Recovers jobs using the configured processing lease timeout.
     *
     * @return number of jobs selected for recovery
     */
    public int recoverPendingJobsOnStartup() {
        return recoverPendingJobs(
                Instant.now(),
                Duration.ofMillis(processingLeaseTimeoutMs)
        );
    }

    /**
     * Re-enqueues due submitted jobs and stale processing jobs.
     *
     * @param now timestamp used to evaluate recovery eligibility
     * @param processingLeaseTimeout processing lease timeout
     * @return number of jobs selected for recovery
     */
    public int recoverPendingJobs(Instant now, Duration processingLeaseTimeout) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(processingLeaseTimeout, "processingLeaseTimeout");
        Instant staleProcessingBefore = now.minus(processingLeaseTimeout);
        List<ConversionJob> recoverableJobs = repository.findRecoverableJobs(now, staleProcessingBefore);
        recoverableJobs.forEach(job -> {
            if (job.getStatus() == ConversionJobStatus.PROCESSING) {
                stateStore.scheduleRetry(job.getJobId(), "worker lease expired; retry queued", Instant.now());
            }
            enqueue(job.getJobId());
        });
        return recoverableJobs.size();
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

        if (artifactStore.getPdf(jobId).isEmpty()) {
            artifactStore.putPdf(jobId, pdfArtifactGenerator.generatePdf(job));
        }
        return "/artifacts/" + jobId + ".pdf";
    }

    private static ConversionJobStateStore stateStoreFrom(ConversionJobRepository repository) {
        if (repository instanceof ConversionJobStateStore conversionJobStateStore) {
            return conversionJobStateStore;
        }

        return new RepositoryBackedConversionJobStateStore(repository);
    }
}
