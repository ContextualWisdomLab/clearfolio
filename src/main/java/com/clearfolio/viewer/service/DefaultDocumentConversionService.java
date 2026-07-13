package com.clearfolio.viewer.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.RepositoryBackedConversionJobStateStore;

/**
 * Default implementation that validates uploads, deduplicates by content hash,
 * and enqueues newly created conversion jobs.
 *
 * <p>When the uploaded source is already a PDF (declared by extension or
 * content type and confirmed by the {@code %PDF-} magic header), the original
 * bytes are seeded into the artifact store so the worker serves the uploaded
 * document as-is instead of generating a placeholder.
 */
@Service
public class DefaultDocumentConversionService implements DocumentConversionService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
            DefaultDocumentConversionService.class);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PDF_EXTENSION_SUFFIX = ".pdf";
    private static final byte[] PDF_MAGIC_HEADER = {'%', 'P', 'D', 'F', '-'};
    private final ConversionJobRepository repository;
    private final ConversionJobStateStore stateStore;
    private final DocumentValidationService validationService;
    private final ConversionWorker conversionWorker;
    private final ArtifactStore artifactStore;
    private final int maxRetryAttempts;
    private final long maxUploadSizeBytes;

    /**
     * Creates the conversion service with repository, validation, worker, and
     * artifact store dependencies.
     *
     * @param repository conversion job repository
     * @param stateStore conversion job lifecycle state store
     * @param validationService document validation service
     * @param conversionWorker conversion worker
     * @param artifactStore generated artifact store used for PDF passthrough seeding
     * @param conversionProperties conversion configuration values
     */
    @Autowired
    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ArtifactStore artifactStore,
            ConversionProperties conversionProperties) {
        this.repository = repository;
        this.stateStore = stateStore;
        this.validationService = validationService;
        this.conversionWorker = conversionWorker;
        this.artifactStore = artifactStore;
        this.maxRetryAttempts = conversionProperties.getMaxRetryAttempts();
        this.maxUploadSizeBytes = conversionProperties.getMaxUploadSizeBytes();
    }

    /**
     * Creates the conversion service with an isolated in-memory artifact store
     * for PDF passthrough seeding; intended for tests and legacy wiring.
     *
     * @param repository conversion job repository
     * @param stateStore conversion job lifecycle state store
     * @param validationService document validation service
     * @param conversionWorker conversion worker
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ConversionProperties conversionProperties) {
        this(
                repository,
                stateStore,
                validationService,
                conversionWorker,
                new InMemoryArtifactStore(),
                conversionProperties
        );
    }

    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ConversionProperties conversionProperties) {
        this(
                repository,
                validationService,
                conversionWorker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                conversionProperties
        );
    }

    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ArtifactStore artifactStore,
            ConversionProperties conversionProperties) {
        this(
                repository,
                stateStoreFrom(repository),
                validationService,
                conversionWorker,
                artifactStore,
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
                sanitizeFilename(file.getOriginalFilename()),
                file.getContentType(),
                contentHash,
                file.getSize(),
                maxRetryAttempts
        );

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(job);
        if (result.created()) {
            seedPdfPassthroughArtifact(result.canonicalJob(), file);
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
    public boolean deleteJob(UUID jobId, TenantContext tenantContext) {
        if (tenantContext == null) {
            return false;
        }

        Optional<ConversionJob> job = repository.findByTenantAndId(tenantContext.tenantId(), jobId);
        if (job.isEmpty()) {
            return false;
        }

        deleteJob(job.get().getJobId());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteJob(UUID jobId) {
        try {
            artifactStore.deletePdf(jobId);
        } catch (Exception ex) {
            log.warn("Failed to delete artifact for job {}", jobId, ex);
        }
        repository.deleteById(jobId);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<ConversionJob> getAllJobs() {
        return repository.findAll();
    }

    private void seedPdfPassthroughArtifact(ConversionJob job, MultipartFile file) {
        if (!declaresPdfSource(job.getOriginalFileName(), job.getContentType())) {
            return;
        }

        byte[] sourceBytes;
        try {
            sourceBytes = file.getBytes();
        } catch (IOException ex) {
            // Fall back to the worker's placeholder conversion path.
            return;
        }

        if (!hasPdfMagicHeader(sourceBytes)) {
            return;
        }

        artifactStore.putPdf(job.getJobId(), sourceBytes);
    }

    static boolean declaresPdfSource(String fileName, String contentType) {
        if (contentType != null) {
            String normalized = contentType.strip().toLowerCase(Locale.ROOT);
            if (normalized.equals(PDF_CONTENT_TYPE) || normalized.startsWith(PDF_CONTENT_TYPE + ";")) {
                return true;
            }
        }

        if (fileName == null) {
            return false;
        }

        return fileName.strip().toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION_SUFFIX);
    }

    static boolean hasPdfMagicHeader(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_MAGIC_HEADER.length) {
            return false;
        }

        for (int index = 0; index < PDF_MAGIC_HEADER.length; index++) {
            if (bytes[index] != PDF_MAGIC_HEADER[index]) {
                return false;
            }
        }

        return true;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        if (filename.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("File name contains null byte.");
        }
        String cleanPath = org.springframework.util.StringUtils.cleanPath(filename);
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash != -1) {
            return cleanPath.substring(lastSlash + 1);
        }
        return cleanPath;
    }

    private String contentHash(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;

            while ((read = stream.read(buffer)) != -1) {
                totalRead += read;
                if (totalRead > maxUploadSizeBytes) {
                    throw new IllegalArgumentException("File size exceeds maximum allowed upload size.");
                }
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
