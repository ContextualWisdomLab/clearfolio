package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;

class DefaultDocumentConversionServiceTest {

    @Test
    void getAllJobsReturnsAllJobsFromRepository() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties props = new ConversionProperties();
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DocumentValidationService() {
                    @Override
                    public void validateOrThrow(org.springframework.web.multipart.MultipartFile file) {
                    }
                },
                id -> {},
                props
        );

        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "a.pdf", "application/pdf", "hash-a", 100L);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "b.pdf", "application/pdf", "hash-b", 100L);
        repository.save(job1);
        repository.save(job2);

        Iterable<ConversionJob> allJobs = service.getAllJobs();
        int count = 0;
        boolean found1 = false;
        boolean found2 = false;
        for (ConversionJob job : allJobs) {
            count++;
            if (job.getJobId().equals(job1.getJobId())) {
                found1 = true;
            }
            if (job.getJobId().equals(job2.getJobId())) {
                found2 = true;
            }
        }

        assertEquals(2, count);
        assertTrue(found1);
        assertTrue(found2);
    }

    @Test
    void submitWithOverrideDelegatesPolicyHeadersToValidationService() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        AtomicReference<PolicyOverrideRequest> capturedOverride = new AtomicReference<>();
        AtomicInteger validationCallCount = new AtomicInteger();
        DocumentValidationService validationService = new DocumentValidationService() {
            @Override
            public void validateOrThrow(MultipartFile file) {
            }

            @Override
            public void validateOrThrow(MultipartFile file, PolicyOverrideRequest overrideRequest) {
                validationCallCount.incrementAndGet();
                capturedOverride.set(overrideRequest);
            }
        };
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                validationService,
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.hwp",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID jobId = service.submit(file, PolicyOverrideRequest.of("true", "token-123", "approver-1"));

        assertNotEquals(new UUID(0L, 0L), jobId);
        assertEquals(1, validationCallCount.get());
        assertEquals("true", capturedOverride.get().policyOverride());
        assertEquals("token-123", capturedOverride.get().approvalToken());
        assertEquals("approver-1", capturedOverride.get().approverId());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitWithNullOverrideFallsBackToNoneOverride() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        AtomicReference<PolicyOverrideRequest> capturedOverride = new AtomicReference<>();
        DocumentValidationService validationService = new DocumentValidationService() {
            @Override
            public void validateOrThrow(MultipartFile file) {
            }

            @Override
            public void validateOrThrow(MultipartFile file, PolicyOverrideRequest overrideRequest) {
                capturedOverride.set(overrideRequest);
            }
        };
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                validationService,
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID jobId = service.submit(file, null);

        assertNotEquals(new UUID(0L, 0L), jobId);
        assertSame(PolicyOverrideRequest.none(), capturedOverride.get());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitStoresTenantAndSubjectMetadataOnJob() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );
        TenantContext tenantContext = new TenantContext(
                "tenant-a",
                "subject-a",
                Set.of(TenantPermissions.JOB_CREATE)
        );

        UUID jobId = service.submit(file, PolicyOverrideRequest.none(), tenantContext);

        ConversionJob job = repository.findById(jobId).orElseThrow();
        assertEquals("tenant-a", job.getTenantId());
        assertEquals("subject-a", job.getSubjectId());
    }

    @Test
    void submitWithNullTenantContextFallsBackToDemoOwnership() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID jobId = service.submit(file, PolicyOverrideRequest.none(), null);

        ConversionJob job = repository.findById(jobId).orElseThrow();
        assertEquals(TenantContext.DEMO_TENANT_ID, job.getTenantId());
        assertEquals(TenantContext.DEMO_SUBJECT_ID, job.getSubjectId());
    }

    @Test
    void returnsSameJobIdForDuplicatePayloads() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID first = service.submit(file);
        UUID second = service.submit(file);

        assertEquals(first, second);
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void returnsDifferentJobIdsForDifferentPayloads() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile firstFile = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "different-content".getBytes()
        );

        UUID first = service.submit(firstFile);
        UUID second = service.submit(secondFile);

        assertNotEquals(first, second);
        assertEquals(2, worker.enqueuedCount());
    }

    @Test
    void returnsSameJobForConcurrentDuplicatePayloads() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer-concurrent".getBytes()
        );

        ExecutorService executor = Executors.newFixedThreadPool(8);
        Callable<UUID> task = () -> service.submit(file);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(task));
        }

        Set<UUID> jobIds = new HashSet<>();
        for (Future<UUID> future : futures) {
            try {
                jobIds.add(future.get());
            } catch (ExecutionException ex) {
                throw new AssertionError("worker invocation failed", ex);
            }
        }
        executor.shutdownNow();
        assertEquals(1, jobIds.size());
        assertEquals(1, worker.enqueuedCount());
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void submitEnqueuesWhenRepositoryReportsCreatedWithDifferentCanonicalJobId() {
        RecordingConversionWorker worker = new RecordingConversionWorker();
        UUID canonicalId = UUID.randomUUID();
        ConversionJob canonical = new ConversionJob(
                canonicalId,
                "canonical.docx",
                "application/octet-stream",
                "canonical-hash",
                10L,
                3
        );
        ConversionJobRepository repository = new FindOrStoreOnlyRepository(
                candidate -> new ConversionJobRepository.FindOrStoreResult(canonical, true)
        );

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID submitted = service.submit(file);

        assertEquals(canonicalId, submitted);
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSkipsEnqueueWhenRepositoryReportsExistingEvenForCandidateCanonical() {
        RecordingConversionWorker worker = new RecordingConversionWorker();
        ConversionJobRepository repository = new FindOrStoreOnlyRepository(
                candidate -> new ConversionJobRepository.FindOrStoreResult(candidate, false)
        );

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID submitted = service.submit(file);

        assertEquals(0, worker.enqueuedCount());
        assertNotEquals(new UUID(0L, 0L), submitted);
    }

    @Test
    void getJobReturnsRepositoryEntry() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-job",
                1L,
                3
        );
        repository.save(job);

        assertTrue(service.getJob(job.getJobId()).isPresent());
        assertSame(job, service.getJob(job.getJobId()).orElseThrow());
    }

    @Test
    void retryDeadLetteredResetsJobAndEnqueuesWorker() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-retry",
                1L,
                3
        );
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        repository.save(job);

        RetryDeadLetterResult retryResult = service.retryDeadLettered(job.getJobId(), "operator-9");

        assertEquals(RetryDeadLetterResult.ACCEPTED, retryResult);
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertEquals(0, job.getAttemptCount());
        assertTrue(job.getStatusMessage().contains("operator-9"));
        assertEquals(1, worker.enqueuedCount());
        assertEquals(job.getJobId(), worker.lastEnqueuedJobId());
    }

    @Test
    void retryDeadLetteredUsesStateStoreTransition() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingStateStore stateStore = new RecordingStateStore(repository);
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                stateStore,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-state-store-retry",
                1L,
                3
        );
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        repository.save(job);

        RetryDeadLetterResult retryResult = service.retryDeadLettered(job.getJobId(), "operator-9");

        assertEquals(RetryDeadLetterResult.ACCEPTED, retryResult);
        assertEquals(List.of("retryDeadLettered"), stateStore.calls());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void retryDeadLetteredReturnsNotFoundWhenJobMissing() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        RetryDeadLetterResult retryResult = service.retryDeadLettered(UUID.randomUUID(), "operator-9");

        assertEquals(RetryDeadLetterResult.NOT_FOUND, retryResult);
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void retryDeadLetteredReturnsNotEligibleWhenJobIsNotDeadLettered() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-not-dead-lettered",
                1L,
                3
        );
        repository.save(job);

        RetryDeadLetterResult retryResult = service.retryDeadLettered(job.getJobId(), "operator-9");

        assertEquals(RetryDeadLetterResult.NOT_ELIGIBLE, retryResult);
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void retryDeadLetteredReturnsNotEligibleWhenJobIsProcessing() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-processing",
                1L,
                3
        );
        assertTrue(job.markProcessing("already processing"));
        repository.save(job);

        RetryDeadLetterResult retryResult = service.retryDeadLettered(job.getJobId(), "operator-9");

        assertEquals(RetryDeadLetterResult.NOT_ELIGIBLE, retryResult);
        assertEquals(ConversionJobStatus.PROCESSING, job.getStatus());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void retryDeadLetteredConcurrentAttemptsAcceptExactlyOneAndEnqueueOnce() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-concurrent-retry",
                1L,
                3
        );
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        repository.save(job);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<RetryDeadLetterResult> retryTask = () -> {
            ready.countDown();
            if (!start.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("retry race setup timed out");
            }
            return service.retryDeadLettered(job.getJobId(), "operator-9");
        };

        Future<RetryDeadLetterResult> first = executor.submit(retryTask);
        Future<RetryDeadLetterResult> second = executor.submit(retryTask);

        assertTrue(ready.await(1, TimeUnit.SECONDS));
        start.countDown();

        RetryDeadLetterResult firstResult = first.get();
        RetryDeadLetterResult secondResult = second.get();
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        int acceptedCount = 0;
        int notEligibleCount = 0;
        if (firstResult == RetryDeadLetterResult.ACCEPTED) {
            acceptedCount++;
        }
        if (secondResult == RetryDeadLetterResult.ACCEPTED) {
            acceptedCount++;
        }
        if (firstResult == RetryDeadLetterResult.NOT_ELIGIBLE) {
            notEligibleCount++;
        }
        if (secondResult == RetryDeadLetterResult.NOT_ELIGIBLE) {
            notEligibleCount++;
        }

        assertEquals(1, acceptedCount);
        assertEquals(1, notEligibleCount);
        assertEquals(1, worker.enqueuedCount());
        assertEquals(job.getJobId(), worker.lastEnqueuedJobId());
    }

    @Test
    void submitSeedsArtifactStoreWithOriginalBytesForPdfUpload() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                },
                worker,
                artifactStore,
                new ConversionProperties()
        );

        byte[] original = "%PDF-1.7\noriginal-sheet-music".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "score.pdf",
                "application/pdf",
                original
        );

        UUID jobId = service.submit(file);

        byte[] seeded = artifactStore.getPdf(jobId).orElseThrow();
        assertArrayEquals(original, seeded);
        assertEquals("%PDF-", new String(seeded, 0, 5, StandardCharsets.UTF_8));
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSeedsArtifactStoreWhenOnlyExtensionDeclaresPdf() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                },
                worker,
                artifactStore,
                new ConversionProperties()
        );

        byte[] original = "%PDF-1.4\nextension-only".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Score.PDF",
                "application/octet-stream",
                original
        );

        UUID jobId = service.submit(file);

        assertArrayEquals(original, artifactStore.getPdf(jobId).orElseThrow());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSkipsPassthroughWhenPdfDeclarationLacksMagicHeader() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                },
                worker,
                artifactStore,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "MZ-not-actually-a-pdf".getBytes(StandardCharsets.UTF_8)
        );

        UUID jobId = service.submit(file);

        assertTrue(artifactStore.getPdf(jobId).isEmpty());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSkipsPassthroughForNonPdfUpload() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                },
                worker,
                artifactStore,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                "hello-viewer".getBytes(StandardCharsets.UTF_8)
        );

        UUID jobId = service.submit(file);

        assertTrue(artifactStore.getPdf(jobId).isEmpty());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSkipsPassthroughWhenSourceBytesCannotBeRead() throws Exception {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                repository,
                file -> {
                },
                worker,
                artifactStore,
                new ConversionProperties()
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("broken.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(5L);
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream("%PDF-".getBytes(StandardCharsets.UTF_8)));
        when(file.getBytes()).thenThrow(new IOException("bytes unavailable"));

        UUID jobId = service.submit(file);

        assertTrue(artifactStore.getPdf(jobId).isEmpty());
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void declaresPdfSourceMatchesContentTypeAndExtensionVariants() {
        assertTrue(DefaultDocumentConversionService.declaresPdfSource(null, "application/pdf"));
        assertTrue(DefaultDocumentConversionService.declaresPdfSource(
                null,
                "  APPLICATION/PDF; charset=UTF-8  "
        ));
        assertTrue(DefaultDocumentConversionService.declaresPdfSource("Score.PDF", null));
        assertTrue(DefaultDocumentConversionService.declaresPdfSource("score.pdf", "text/plain"));
        assertFalse(DefaultDocumentConversionService.declaresPdfSource(null, null));
        assertFalse(DefaultDocumentConversionService.declaresPdfSource(null, "application/pdfx"));
        assertFalse(DefaultDocumentConversionService.declaresPdfSource(
                "report.docx",
                "application/octet-stream"
        ));
    }

    @Test
    void hasPdfMagicHeaderChecksLeadingBytes() {
        assertTrue(DefaultDocumentConversionService.hasPdfMagicHeader(
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8)
        ));
        assertFalse(DefaultDocumentConversionService.hasPdfMagicHeader(null));
        assertFalse(DefaultDocumentConversionService.hasPdfMagicHeader(
                "%PDF".getBytes(StandardCharsets.UTF_8)
        ));
        assertFalse(DefaultDocumentConversionService.hasPdfMagicHeader(
                "XPDF-1.7".getBytes(StandardCharsets.UTF_8)
        ));
        assertFalse(DefaultDocumentConversionService.hasPdfMagicHeader(
                "%PDX-1.7".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void deleteJobWithNullTenantContextReturnsFalse() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                new ConversionProperties()
        );

        boolean deleted = service.deleteJob(UUID.randomUUID(), null);

        assertFalse(deleted);
    }

    @Test
    void testLegacyConstructor() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new com.clearfolio.viewer.repository.RepositoryBackedConversionJobStateStore(repository),
                mock(DocumentValidationService.class),
                worker,
                new ConversionProperties()
        );
        assertNotNull(service);
    }

    @Test
    void submitThrowsWhenUploadSizeExceedsMaximum() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        ConversionProperties props = new ConversionProperties();
        props.setMaxUploadSizeBytes(10L); // Set a small max size for testing

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                props
        );

        byte[] largeContent = new byte[15]; // Content larger than max size
        MultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                largeContent
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.submit(file));

        assertEquals("File size exceeds maximum allowed upload size.", error.getMessage());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void submitThrowsWhenUploadCannotBeReadForHashing() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("read failed"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.submit(file));

        assertEquals("Unable to read upload for hashing", error.getMessage());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void submitThrowsWhenSha256DigestIsUnavailable() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore(),
                new ConversionProperties()
        );

        MultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
        );

        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.submit(file));
            assertEquals("SHA-256 digest unavailable", error.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }

        assertEquals(0, worker.enqueuedCount());
    }


    @Test
    void deleteJobWithTenantContextDeletesOwnedJobAndArtifact() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        com.clearfolio.viewer.artifact.InMemoryArtifactStore artifactStore =
                new com.clearfolio.viewer.artifact.InMemoryArtifactStore();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                artifactStore,
                new ConversionProperties()
        );
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "subject-a",
                "report.docx",
                "application/octet-stream",
                "hash-delete-owned",
                10L,
                3
        );
        repository.save(job);
        artifactStore.putPdf(jobId, new byte[] {1, 2, 3});

        boolean deleted = service.deleteJob(
                jobId,
                new TenantContext("tenant-a", "subject-a", Set.of(TenantPermissions.JOB_DELETE))
        );

        assertTrue(deleted);
        assertTrue(repository.findById(jobId).isEmpty());
        assertTrue(artifactStore.getPdf(jobId).isEmpty());
    }

    @Test
    void deleteJobWithTenantContextLeavesOtherTenantJobUntouched() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        com.clearfolio.viewer.artifact.ArtifactStore artifactStore =
                mock(com.clearfolio.viewer.artifact.ArtifactStore.class);
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                artifactStore,
                new ConversionProperties()
        );
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "tenant-a",
                "subject-a",
                "report.docx",
                "application/octet-stream",
                "hash-delete-cross-tenant",
                10L,
                3
        );
        repository.save(job);

        boolean deleted = service.deleteJob(
                jobId,
                new TenantContext("tenant-b", "subject-b", Set.of(TenantPermissions.JOB_DELETE))
        );

        assertFalse(deleted);
        assertSame(job, repository.findById(jobId).orElseThrow());
        org.mockito.Mockito.verifyNoInteractions(artifactStore);
    }

    @Test
    void deleteJobSucceedsWhenArtifactDeletionFails() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionWorker worker = mock(ConversionWorker.class);
        DocumentValidationService validationService = mock(DocumentValidationService.class);
        com.clearfolio.viewer.artifact.ArtifactStore artifactStore = mock(com.clearfolio.viewer.artifact.ArtifactStore.class);

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                validationService,
                worker,
                artifactStore,
                new ConversionProperties()
        );

        UUID jobId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new RuntimeException("S3 is down")).when(artifactStore).deletePdf(jobId);

        service.deleteJob(jobId);

        org.mockito.Mockito.verify(repository).deleteById(jobId);
    }

    private static class RecordingConversionWorker implements ConversionWorker {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<UUID> lastJobId = new AtomicReference<>();

        @Override
        public void enqueue(UUID jobId) {
            lastJobId.set(jobId);
            count.incrementAndGet();
        }

        int enqueuedCount() {
            return count.get();
        }

        UUID lastEnqueuedJobId() {
            return lastJobId.get();
        }
    }

    private static class FindOrStoreOnlyRepository implements ConversionJobRepository {
        private final java.util.function.Function<ConversionJob, ConversionJobRepository.FindOrStoreResult> finder;

        FindOrStoreOnlyRepository(java.util.function.Function<ConversionJob, ConversionJobRepository.FindOrStoreResult> finder) {
            this.finder = finder;
        }

        @Override
        public ConversionJob save(ConversionJob job) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<ConversionJob> findById(UUID jobId) {
            return Optional.empty();
        }

        @Override
        public Optional<ConversionJob> findByContentHash(String contentHash) {
            return Optional.empty();
        }

        @Override
        public java.util.List<ConversionJob> findAll() {
            return java.util.List.of();
        }

        @Override
        public ConversionJobRepository.FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
            return finder.apply(candidate);
        }

        @Override
        public void deleteById(UUID jobId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class RecordingStateStore implements ConversionJobStateStore {
        private final ConversionJobStateStore delegate;
        private final List<String> calls = new ArrayList<>();

        RecordingStateStore(ConversionJobStateStore delegate) {
            this.delegate = delegate;
        }

        List<String> calls() {
            return List.copyOf(calls);
        }

        @Override
        public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
            calls.add("claimForProcessing");
            return delegate.claimForProcessing(jobId, now);
        }

        @Override
        public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
            calls.add("scheduleRetry");
            delegate.scheduleRetry(jobId, message, retryAt);
        }

        @Override
        public void markSucceeded(UUID jobId, String resourcePath, String message) {
            calls.add("markSucceeded");
            delegate.markSucceeded(jobId, resourcePath, message);
        }

        @Override
        public void markDeadLettered(UUID jobId, String message) {
            calls.add("markDeadLettered");
            delegate.markDeadLettered(jobId, message);
        }

        @Override
        public boolean retryDeadLettered(UUID jobId, String operatorId) {
            calls.add("retryDeadLettered");
            return delegate.retryDeadLettered(jobId, operatorId);
        }
    }
}
