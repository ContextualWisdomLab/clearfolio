package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;

/**
 * In-memory repository implementation for conversion job persistence.
 */
@Repository
public class InMemoryConversionJobRepository implements ConversionJobRepository, ConversionJobStateStore {

    private static final String EVENT_SUBMITTED = "conversion.job.submitted";
    private static final String EVENT_DEDUPE_HIT = "conversion.job.dedupe_hit";
    private static final String EVENT_PROCESSING_STARTED = "conversion.processing.started";
    private static final String EVENT_RETRY_SCHEDULED = "conversion.retry.scheduled";
    private static final String EVENT_JOB_SUCCEEDED = "conversion.job.succeeded";
    private static final String EVENT_JOB_FAILED = "conversion.job.failed";
    private static final String EVENT_RETRY_ACCEPTED = "conversion.retry.accepted";

    private final ConcurrentHashMap<UUID, ConversionJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> jobsByTenantAndContentHash = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ConversionJobLifecycleEvent> lifecycleEvents = new ConcurrentLinkedQueue<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJob save(ConversionJob job) {
        jobs.put(job.getJobId(), job);
        if (job.getContentHash() != null && !job.getContentHash().isBlank()) {
            jobsByTenantAndContentHash.putIfAbsent(contentKey(job.getTenantId(), job.getContentHash()), job.getJobId());
        }
        return job;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJobRepository.FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
        String contentHash = candidate.getContentHash();
        if (contentHash == null || contentHash.isBlank()) {
            save(candidate);
            appendLifecycleEvent(candidate, EVENT_SUBMITTED, null);
            return new ConversionJobRepository.FindOrStoreResult(candidate, true);
        }

        String contentKey = contentKey(candidate.getTenantId(), contentHash);
        AtomicBoolean created = new AtomicBoolean(false);
        AtomicReference<ConversionJob> canonical = new AtomicReference<>();
        jobsByTenantAndContentHash.compute(
                contentKey,
                (key, existingJobId) -> {
                    if (existingJobId != null) {
                        ConversionJob existing = jobs.get(existingJobId);
                        if (existing != null) {
                            canonical.set(existing);
                            return existingJobId;
                        }
                    }

                    jobs.put(candidate.getJobId(), candidate);
                    created.set(true);
                    canonical.set(candidate);
                    return candidate.getJobId();
                }
        );

        ConversionJob storedJob = canonical.get();
        appendLifecycleEvent(storedJob, created.get() ? EVENT_SUBMITTED : EVENT_DEDUPE_HIT, null);
        return new ConversionJobRepository.FindOrStoreResult(canonical.get(), created.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> findByContentHash(String contentHash) {
        return findByTenantAndContentHash("buyer-demo", contentHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> findByTenantAndContentHash(String tenantId, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }

        UUID jobId = jobsByTenantAndContentHash.get(contentKey(tenantId, contentHash));
        if (jobId == null) {
            return Optional.empty();
        }

        return findById(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConversionJob> findAll() {
        return List.copyOf(jobs.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(UUID jobId) {
        ConversionJob removed = jobs.remove(jobId);
        if (removed != null && removed.getContentHash() != null && !removed.getContentHash().isBlank()) {
            jobsByTenantAndContentHash.remove(contentKey(removed.getTenantId(), removed.getContentHash()), jobId);
        }
    }

    /**
     * Returns lifecycle events for a conversion job.
     *
     * @param jobId conversion job identifier
     * @return append-only lifecycle events for the job
     */
    public List<ConversionJobLifecycleEvent> findLifecycleEventsByJobId(UUID jobId) {
        return lifecycleEvents.stream()
                .filter(event -> event.jobId().equals(jobId))
                .toList();
    }

    /**
     * Returns lifecycle events for a tenant.
     *
     * @param tenantId tenant identifier
     * @return append-only lifecycle events for the tenant
     */
    public List<ConversionJobLifecycleEvent> findLifecycleEventsByTenantId(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        return lifecycleEvents.stream()
                .filter(event -> event.tenantId().equals(normalizedTenantId))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
        Optional<ConversionJob> job = findById(jobId);
        if (job.isEmpty() || !job.get().isReadyForProcessing(now)) {
            return Optional.empty();
        }

        ConversionJobStatus statusBefore = job.get().getStatus();
        if (!job.get().markProcessing("conversion started")) {
            return Optional.empty();
        }

        appendLifecycleEvent(job.get(), EVENT_PROCESSING_STARTED, statusBefore);
        return job;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
        findById(jobId).ifPresent(job -> {
            ConversionJobStatus statusBefore = job.getStatus();
            job.markRetryScheduled(message, retryAt);
            appendLifecycleEvent(job, EVENT_RETRY_SCHEDULED, statusBefore);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markSucceeded(UUID jobId, String resourcePath, String message) {
        findById(jobId).ifPresent(job -> {
            ConversionJobStatus statusBefore = job.getStatus();
            job.markSucceeded(resourcePath, message);
            appendLifecycleEvent(job, EVENT_JOB_SUCCEEDED, statusBefore);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markDeadLettered(UUID jobId, String message) {
        findById(jobId).ifPresent(job -> {
            ConversionJobStatus status = job.getStatus();
            if (status == ConversionJobStatus.SUBMITTED || status == ConversionJobStatus.PROCESSING) {
                ConversionJobStatus statusBefore = job.getStatus();
                job.markDeadLettered(message);
                appendLifecycleEvent(job, EVENT_JOB_FAILED, statusBefore);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryDeadLettered(UUID jobId, String operatorId) {
        Optional<ConversionJob> job = findById(jobId);
        if (job.isEmpty()) {
            return false;
        }

        ConversionJobStatus statusBefore = job.get().getStatus();
        if (!job.get().retryDeadLetteredToSubmitted(operatorId)) {
            return false;
        }

        appendLifecycleEvent(job.get(), EVENT_RETRY_ACCEPTED, statusBefore);
        return true;
    }

    private String contentKey(String tenantId, String contentHash) {
        return normalizeTenantId(tenantId) + "\u001f" + contentHash;
    }

    private String normalizeTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "buyer-demo" : tenantId.strip();
    }

    private void appendLifecycleEvent(
            ConversionJob job,
            String eventType,
            ConversionJobStatus statusBefore
    ) {
        lifecycleEvents.add(new ConversionJobLifecycleEvent(
                UUID.randomUUID(),
                job.getJobId(),
                job.getTenantId(),
                eventType,
                ConversionJobLifecycleEvent.CURRENT_VERSION,
                Instant.now(),
                statusBefore,
                job.getStatus(),
                job.getAttemptCount(),
                job.getRetryAt()
        ));
    }
}
