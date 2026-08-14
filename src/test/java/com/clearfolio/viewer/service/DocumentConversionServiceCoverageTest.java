package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that the compatibility delete contract fails closed before mutation.
 */
class DocumentConversionServiceCoverageTest {

    @Test
    void tenantScopedDeleteRejectsMissingContextAndMissingJobBeforeMutation() {
        UUID jobId = UUID.randomUUID();
        AtomicInteger lookupCount = new AtomicInteger();
        AtomicInteger deleteCount = new AtomicInteger();
        DocumentConversionService service = new DocumentConversionService() {
            @Override
            public UUID submit(MultipartFile file) {
                return UUID.randomUUID();
            }

            @Override
            public Optional<ConversionJob> getJob(UUID requestedJobId) {
                lookupCount.incrementAndGet();
                return Optional.empty();
            }

            @Override
            public RetryDeadLetterResult retryDeadLettered(UUID requestedJobId, String operatorId) {
                return RetryDeadLetterResult.NOT_FOUND;
            }

            @Override
            public void deleteJob(UUID requestedJobId) {
                deleteCount.incrementAndGet();
            }

            @Override
            public Iterable<ConversionJob> getAllJobs() {
                return java.util.List.of();
            }
        };

        assertFalse(service.deleteJob(jobId, null));
        assertEquals(0, lookupCount.get());
        assertEquals(0, deleteCount.get());

        assertFalse(service.deleteJob(
                jobId,
                new TenantContext("tenant-a", "subject-a", Set.of())
        ));
        assertEquals(1, lookupCount.get());
        assertEquals(0, deleteCount.get());
    }

    @Test
    void tenantScopedListFailsClosedAndReturnsOnlyOwnedJobs() {
        ConversionJob owned = jobForTenant("tenant-a", "owned.pdf");
        ConversionJob other = jobForTenant("tenant-b", "other.pdf");
        RecordingDocumentConversionService service =
                new RecordingDocumentConversionService(
                        java.util.List.of(owned, other));

        assertIterableEquals(
                java.util.List.of(),
                service.getAllJobs(null));
        assertIterableEquals(
                java.util.List.of(owned),
                service.getAllJobs(contextFor("tenant-a")));
    }

    @Test
    void tenantScopedRetryFailsClosedBeforeLegacyMutation() {
        UUID ownedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        RecordingDocumentConversionService service =
                new RecordingDocumentConversionService(java.util.List.of(
                        jobForTenant(ownedId, "tenant-a", "owned.pdf"),
                        jobForTenant(otherId, "tenant-b", "other.pdf")));

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(ownedId, "admin", null));
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(
                        UUID.randomUUID(), "admin", contextFor("tenant-a")));
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(
                        otherId, "admin", contextFor("tenant-a")));
        assertEquals(0, service.retryCount.get());

        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(
                        ownedId, "admin", contextFor("tenant-a")));
        assertEquals(1, service.retryCount.get());
    }

    private static TenantContext contextFor(final String tenantId) {
        return new TenantContext(tenantId, "admin-1", Set.of());
    }

    private static ConversionJob jobForTenant(
            final String tenantId,
            final String fileName) {
        return jobForTenant(UUID.randomUUID(), tenantId, fileName);
    }

    private static ConversionJob jobForTenant(
            final UUID jobId,
            final String tenantId,
            final String fileName) {
        return new ConversionJob(
                jobId,
                tenantId,
                "subject-1",
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3);
    }

    /**
     * Minimal compatibility implementation used to exercise interface guards.
     */
    private static final class RecordingDocumentConversionService
            implements DocumentConversionService {
        private final java.util.List<ConversionJob> jobs;
        private final AtomicInteger retryCount = new AtomicInteger();

        RecordingDocumentConversionService(
                final java.util.List<ConversionJob> conversionJobs) {
            this.jobs = conversionJobs;
        }

        @Override
        public UUID submit(final MultipartFile file) {
            return UUID.randomUUID();
        }

        @Override
        public Optional<ConversionJob> getJob(final UUID jobId) {
            return jobs.stream()
                    .filter(job -> job.getJobId().equals(jobId))
                    .findFirst();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(
                final UUID jobId,
                final String operatorId) {
            retryCount.incrementAndGet();
            return RetryDeadLetterResult.ACCEPTED;
        }

        @Override
        public void deleteJob(final UUID jobId) {
            // No mutation is needed for the list and retry guard tests.
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return jobs;
        }
    }
}
