package com.clearfolio.viewer.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.lifecycle.ArtifactDeletionCoordinator;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Primary document-service decorator that routes deletion through the durable
 * artifact-cleanup lifecycle while preserving the existing conversion service.
 *
 * <p>Submission, lookup, retry, and listing remain owned by
 * {@link DefaultDocumentConversionService}. Only deletion is intercepted, which
 * keeps the cleanup worker independently replaceable for standalone and MSA
 * deployments without duplicating conversion logic.</p>
 */
@Service
@Primary
public final class DurableDocumentDeletionService implements DocumentConversionService {

    private final DefaultDocumentConversionService delegate;
    private final ArtifactDeletionCoordinator deletionCoordinator;

    /**
     * Creates the durable deletion decorator.
     *
     * @param delegate existing conversion service used for non-deletion behavior
     * @param deletionCoordinator receipt-first artifact deletion coordinator
     * @throws NullPointerException when a required collaborator is absent
     */
    public DurableDocumentDeletionService(
            DefaultDocumentConversionService delegate,
            ArtifactDeletionCoordinator deletionCoordinator
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.deletionCoordinator = Objects.requireNonNull(deletionCoordinator, "deletionCoordinator");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file) {
        return delegate.submit(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        return delegate.submit(file, overrideRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(
            MultipartFile file,
            PolicyOverrideRequest overrideRequest,
            TenantContext tenantContext
    ) {
        return delegate.submit(file, overrideRequest, tenantContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> getJob(UUID jobId) {
        return delegate.getJob(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
        return delegate.retryDeadLettered(jobId, operatorId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RetryDeadLetterResult retryDeadLettered(
            UUID jobId,
            TenantContext tenantContext,
            String operatorId
    ) {
        return delegate.retryDeadLettered(jobId, tenantContext, operatorId);
    }

    /**
     * Routes tenant-owned deletion through the durable receipt lifecycle.
     *
     * @param jobId conversion job identifier
     * @param tenantContext authenticated tenant context
     * @return false when the context is absent or the job is missing/cross-tenant;
     *         true when the deletion lifecycle is accepted or already exists
     */
    @Override
    public boolean deleteJob(UUID jobId, TenantContext tenantContext) {
        if (tenantContext == null) {
            return false;
        }
        return deletionCoordinator.deleteForTenant(jobId, tenantContext.tenantId());
    }

    /**
     * Routes the legacy compatibility deletion through the durable coordinator.
     *
     * @param jobId conversion job identifier
     */
    @Override
    public void deleteJob(UUID jobId) {
        deletionCoordinator.deleteGlobally(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<ConversionJob> getJobsForTenant(TenantContext tenantContext) {
        return delegate.getJobsForTenant(tenantContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<ConversionJob> getAllJobs() {
        return delegate.getAllJobs();
    }
}
