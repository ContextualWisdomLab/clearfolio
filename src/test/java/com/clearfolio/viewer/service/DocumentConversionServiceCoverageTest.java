package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void tenantScopedDeleteRejectsEveryContextBeforeReadingOrMutating() {
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
        assertEquals(0, lookupCount.get());
        assertEquals(0, deleteCount.get());
    }
}
