package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

class ConversionJobRepositoryTest {

    @Test
    void defaultFindByTenantAndContentHashFiltersLegacyContentHashLookup() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-a",
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        ConversionJobRepository repository = new ConversionJobRepository() {
            @Override
            public ConversionJob save(ConversionJob job) {
                return job;
            }

            @Override
            public Optional<ConversionJob> findById(UUID jobId) {
                return Optional.empty();
            }

            @Override
            public Optional<ConversionJob> findByContentHash(String contentHash) {
                return Optional.of(job);
            }

            @Override
            public FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
                return new FindOrStoreResult(candidate, true);
            }

            @Override
            public List<ConversionJob> findAll() {
                return List.of(job);
            }
        };

        assertSame(job, repository.findByTenantAndContentHash("tenant-a", "hash").orElseThrow());
        assertTrue(repository.findByTenantAndContentHash("tenant-b", "hash").isEmpty());
    }
}
