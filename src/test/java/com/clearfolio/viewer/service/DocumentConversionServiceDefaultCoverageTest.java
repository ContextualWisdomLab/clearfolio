package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Covers compatibility defaults that intentionally return no tenant data until
 * a concrete implementation supplies a persistence-scoped query.
 */
class DocumentConversionServiceDefaultCoverageTest {

    @Test
    void tenantListingDefaultReturnsNoJobs() {
        DocumentConversionService service = new CompatibilityOnlyService();

        assertFalse(service.getJobsForTenant(
                new TenantContext("tenant-a", "subject-a", java.util.Set.of())
        ).iterator().hasNext());
    }

    private static final class CompatibilityOnlyService implements DocumentConversionService {
        @Override
        public UUID submit(MultipartFile file) {
            return UUID.randomUUID();
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            return Optional.empty();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            return RetryDeadLetterResult.NOT_FOUND;
        }

        @Override
        public void deleteJob(UUID jobId) {
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return java.util.List.of();
        }
    }
}
