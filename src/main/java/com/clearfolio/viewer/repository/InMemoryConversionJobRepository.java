package com.clearfolio.viewer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>A job identifier is permanently reserved for the lifetime of this
 * repository instance once it has been stored. Deletion removes the live job
 * and its content-hash index, but it does not make the identifier reusable.
 * This prevents a queued UUID from resolving to a different tenant or lifecycle
 * generation after an authorized retry or other asynchronous handoff.</p>
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
    private static final String IDENTIFIER_COLLISION_MESSAGE = "Conversion job identifier collision.";

    private final ConcurrentHashMap<UUID, ConversionJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> jobsByTenantAndContentHash = new ConcurrentHashMap<>();
    private final Set<UUID> reservedJobIdentifiers = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<ConversionJobLifecycleEvent> lifecycleEvents = new ConcurrentLinkedQueue<>();
    private final Object jobIndexLock = new Object();

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJob save(ConversionJob job) {
        Objects.requireNonNull(job, "job");
        synchronized (jobIndexLock) {
            UUID jobId = Objects.requireNonNull(job.getJobId(), "job.jobId");
            ConversionJob existing = jobs.get(jobId);
            if (existing == job) {
                return existing;
            }
            rejectReservedIdentifier(jobId, existing);

            String contentHash = job.getContentHash();
            String contentIndexKey = contentHash == null || contentHash.isBlank()
                    ? null
                    : contentKey(job.getTenantId(), contentHash);
            reservedJobIdentifiers.add(jobId);
            jobs.put(jobId, job);
            if (contentIndexKey != null) {
                jobsByTenantAndContentHash.putIfAbsent(contentIndexKey, jobId);
            }
            return job;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJobRepository.FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
        Objects.requireNonNull(candidate, "candidate");
        synchronized (jobIndexLock) {
            UUID candidateJobId = Objects.requireNonNull(candidate.getJobId(), "candidate.jobId");
            ConversionJob existingById = jobs.get(candidateJobId);
            if (existingById == candidate) {
                appendLifecycleEvent(existingById, EVENT_DEDUPE_HIT, null);
                return new ConversionJobRepository.FindOrStoreResult(existingById, false);
            }
            rejectReservedIdentifier(candidateJobId, existingById);

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
                        ConversionJob existing = existingJobId == null ? null : jobs.get(existingJobId);
                        if (matchesContentIndex(existing, key)) {
                            canonical.set(existing);
                            return existingJobId;
                        }

                        storeNewCandidate(candidate);
                        created.set(true);
                        canonical.set(candidate);
                        return candidate.getJobId();
                    }
            );

            ConversionJob storedJob = canonical.get();
            appendLifecycleEvent(storedJob, created.get() ? EVENT_SUBMITTED : EVENT_DEDUPE_HIT, null);
            return new ConversionJobRepository.FindOrStoreResult(storedJob, created.get());
        }
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
    public Optional<ConversionJob> findByTenantAndId(String tenantId, UUID jobId) {
        if (tenantId == null || tenantId.isBlank() || jobId == null) {
            return Optional.empty();
        }

        synchronized (jobIndexLock) {
            String normalizedTenantId = tenantId.strip();
            return Optional.ofNullable(jobs.get(jobId))
                    .filter(job -> job.belongsToTenant(normalizedTenantId));
        }
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
        if (tenantId == null || tenantId.isBlank() || contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }

        synchronized (jobIndexLock) {
            String expectedContentKey = contentKey(tenantId, contentHash);
            UUID jobId = jobsByTenantAndContentHash.get(expectedContentKey);
            if (jobId == null) {
                return Optional.empty();
            }

            return findById(jobId).filter(job -> matchesContentIndex(job, expectedContentKey));
        }
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
    public List<ConversionJob> findAllByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        String normalizedTenantId = tenantId.strip();
        return jobs.values().stream()
                .filter(job -> job.belongsToTenant(normalizedTenantId))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteByTenantAndId(String tenantId, UUID jobId) {
        if (tenantId == null || tenantId.isBlank() || jobId == null) {
            return false;
        }

        synchronized (jobIndexLock) {
            String normalizedTenantId = tenantId.strip();
            ConversionJob existing = jobs.get(jobId);
            if (existing == null || !existing.belongsToTenant(normalizedTenantId)) {
                return false;
            }

            jobs.remove(jobId);
            removeContentIndex(existing);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(UUID jobId) {
        synchronized (jobIndexLock) {
            removeContentIndex(jobs.remove(jobId));
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
     * <p>Missing or blank scoped tenant identifiers fail closed and never infer
     * the explicit legacy demo tenant.</p>
     *
     * @param tenantId tenant identifier
     * @return append-only lifecycle events for the tenant, or an empty list when
     *         scoped tenant context is absent
     */
    public List<ConversionJobLifecycleEvent> findLifecycleEventsByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public TenantRetryOutcome retryDeadLetteredForTenant(
            String tenantId,
            UUID jobId,
            String operatorId
    ) {
        if (tenantId == null || tenantId.isBlank() || jobId == null) {
            return TenantRetryOutcome.NOT_FOUND;
        }

        String normalizedTenantId = tenantId.strip();
        AtomicReference<TenantRetryOutcome> outcome = new AtomicReference<>(
                TenantRetryOutcome.NOT_FOUND
        );
        jobs.computeIfPresent(jobId, (ignored, existing) -> {
            if (!existing.belongsToTenant(normalizedTenantId)) {
                return existing;
            }

            ConversionJobStatus statusBefore = existing.getStatus();
            if (!existing.retryDeadLetteredToSubmitted(operatorId)) {
                outcome.set(TenantRetryOutcome.NOT_ELIGIBLE);
                return existing;
            }

            appendLifecycleEvent(existing, EVENT_RETRY_ACCEPTED, statusBefore);
            outcome.set(TenantRetryOutcome.ACCEPTED);
            return existing;
        });
        return outcome.get();
    }

    private void storeNewCandidate(ConversionJob candidate) {
        UUID jobId = Objects.requireNonNull(candidate.getJobId(), "candidate.jobId");
        ConversionJob existing = jobs.get(jobId);
        rejectReservedIdentifier(jobId, existing);
        reservedJobIdentifiers.add(jobId);
        jobs.put(jobId, candidate);
    }

    private void rejectReservedIdentifier(UUID jobId, ConversionJob existing) {
        if (existing != null || reservedJobIdentifiers.contains(jobId)) {
            throw new IllegalStateException(IDENTIFIER_COLLISION_MESSAGE);
        }
    }

    private String contentKey(String tenantId, String contentHash) {
        return normalizeTenantId(tenantId) + "\u001f" + contentHash;
    }

    private String normalizeTenantId(String tenantId) {
        return Objects.requireNonNull(tenantId, "tenantId").strip();
    }

    private boolean matchesContentIndex(ConversionJob job, String expectedContentKey) {
        return expectedContentKey.equals(
                Optional.ofNullable(job)
                        .map(current -> contentKey(current.getTenantId(), current.getContentHash()))
                        .orElse("")
        );
    }

    private void removeContentIndex(ConversionJob removed) {
        if (removed != null && removed.getContentHash() != null && !removed.getContentHash().isBlank()) {
            jobsByTenantAndContentHash.remove(
                    contentKey(removed.getTenantId(), removed.getContentHash()),
                    removed.getJobId()
            );
        }
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
