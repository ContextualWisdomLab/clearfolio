package com.clearfolio.viewer.service;

import java.util.Optional;
import java.util.UUID;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.RepositoryBackedConversionJobStateStore;
import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Default implementation that validates uploads, deduplicates by content hash,
 * and enqueues newly created conversion jobs.
 */
@Service
public class DefaultDocumentConversionService implements DocumentConversionService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private final ConversionJobRepository repository;
    private final ConversionJobStateStore stateStore;
    private final DocumentValidationService validationService;
    private final ConversionWorker conversionWorker;
    private final int maxRetryAttempts;

    /**
     * Creates the conversion service with repository, validation, and worker dependencies.
     *
     * @param repository conversion job repository
     * @param stateStore conversion job lifecycle state store
     * @param validationService document validation service
     * @param conversionWorker conversion worker
     * @param conversionProperties conversion configuration values
     */
    @Autowired
    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ConversionProperties conversionProperties) {
        this.repository = repository;
        this.stateStore = stateStore;
        this.validationService = validationService;
        this.conversionWorker = conversionWorker;
        this.maxRetryAttempts = conversionProperties.getMaxRetryAttempts();
    }

    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ConversionProperties conversionProperties) {
        this(
                repository,
                stateStoreFrom(repository),
                validationService,
                conversionWorker,
                conversionProperties
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file) {
        return submit(file, PolicyOverrideRequest.none());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        return submit(file, overrideRequest, new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                java.util.Set.of()
        ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest, TenantContext tenantContext) {
        PolicyOverrideRequest effectiveOverride = overrideRequest == null
                ? PolicyOverrideRequest.none()
                : overrideRequest;
        TenantContext effectiveTenant = tenantContext == null
                ? new TenantContext(TenantContext.DEMO_TENANT_ID, TenantContext.DEMO_SUBJECT_ID, java.util.Set.of())
                : tenantContext;
        validationService.validateOrThrow(file, effectiveOverride);

        String contentHash = contentHash(file);
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                effectiveTenant.tenantId(),
                effectiveTenant.subjectId(),
                file.getOriginalFilename(),
                file.getContentType(),
                contentHash,
                file.getSize(),
                maxRetryAttempts
        );

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(job);
        if (result.created()) {
            conversionWorker.enqueue(result.canonicalJob().getJobId());
        }

        return result.canonicalJob().getJobId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> getJob(UUID jobId) {
        return repository.findById(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
        Optional<ConversionJob> existing = repository.findById(jobId);
        if (existing.isEmpty()) {
            return RetryDeadLetterResult.NOT_FOUND;
        }

        ConversionJob job = existing.get();
        if (!stateStore.retryDeadLettered(job.getJobId(), operatorId)) {
            return RetryDeadLetterResult.NOT_ELIGIBLE;
        }

        conversionWorker.enqueue(job.getJobId());
        return RetryDeadLetterResult.ACCEPTED;
    }

    private String contentHash(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            byte[] raw = digest.digest();
            // Reused HexFormat for performance
            return HEX_FORMAT.formatHex(raw);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read upload for hashing", ex);
        }
    }

    private static ConversionJobStateStore stateStoreFrom(ConversionJobRepository repository) {
        if (repository instanceof ConversionJobStateStore conversionJobStateStore) {
            return conversionJobStateStore;
        }

        return new RepositoryBackedConversionJobStateStore(repository);
    }
}
