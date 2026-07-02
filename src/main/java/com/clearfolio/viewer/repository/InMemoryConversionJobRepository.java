package com.clearfolio.viewer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * In-memory repository implementation for conversion job persistence.
 */
@Repository
public class InMemoryConversionJobRepository implements ConversionJobRepository {

    private final ConcurrentHashMap<UUID, ConversionJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> jobsByTenantAndContentHash = new ConcurrentHashMap<>();

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

    private String contentKey(String tenantId, String contentHash) {
        String normalizedTenantId = tenantId == null || tenantId.isBlank() ? "buyer-demo" : tenantId.strip();
        return normalizedTenantId + "\u001f" + contentHash;
    }
}
