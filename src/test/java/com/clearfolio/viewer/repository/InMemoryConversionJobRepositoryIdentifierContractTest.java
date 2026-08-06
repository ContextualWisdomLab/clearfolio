package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies immutable conversion-job identifier ownership before content-hash
 * deduplication or secondary-index access.
 */
class InMemoryConversionJobRepositoryIdentifierContractTest {

    @Test
    void exactLiveObjectIsAnIdempotentFindOrStoreHit() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = job(
                UUID.randomUUID(),
                "tenant-north",
                "shared-content-hash"
        );
        repository.save(job);

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(job);

        assertFalse(result.created());
        assertSame(job, result.canonicalJob());
    }

    @Test
    void distinctObjectWithSameTenantHashAndLiveIdentifierIsRejected() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob existing = job(sharedJobId, "tenant-north", "shared-content-hash");
        ConversionJob collision = job(sharedJobId, "tenant-north", "shared-content-hash");
        repository.save(existing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(collision)
        );

        assertEquals("Conversion job identifier collision.", exception.getMessage());
        assertSame(existing, repository.findById(sharedJobId).orElseThrow());
        assertSame(
                existing,
                repository.findByTenantAndContentHash(
                        "tenant-north",
                        "shared-content-hash"
                ).orElseThrow()
        );
    }

    @Test
    void liveIdentifierCollisionIsRejectedBeforeCandidateHashAccess() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob existing = job(sharedJobId, "tenant-north", "existing-content-hash");
        ConversionJob collision = new HashAccessFailureJob(sharedJobId);
        repository.save(existing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(collision)
        );

        assertEquals("Conversion job identifier collision.", exception.getMessage());
        assertSame(existing, repository.findById(sharedJobId).orElseThrow());
    }

    private static ConversionJob job(UUID jobId, String tenantId, String contentHash) {
        return new ConversionJob(
                jobId,
                tenantId,
                "owner",
                "document.pdf",
                "application/pdf",
                contentHash,
                100L,
                3
        );
    }

    /**
     * Candidate whose content hash must never be read after a UUID collision.
     */
    private static final class HashAccessFailureJob extends ConversionJob {

        private HashAccessFailureJob(UUID jobId) {
            super(
                    jobId,
                    "tenant-north",
                    "owner",
                    "collision.pdf",
                    "application/pdf",
                    "constructor-content-hash",
                    100L,
                    3
            );
        }

        @Override
        public String getContentHash() {
            throw new IllegalStateException("candidate content hash was accessed first");
        }
    }
}
