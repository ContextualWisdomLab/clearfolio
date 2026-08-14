package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Proves that process-local job identifiers have immutable ownership and cannot
 * be rebound to a different job object, including after deletion.
 */
class InMemoryConversionJobRepositoryIdentifierReservationTest {

    private static final String COLLISION_MESSAGE = "Conversion job identifier collision.";

    @Test
    void exactLiveObjectIsAnIdempotentFindOrStoreHit() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob job = job(UUID.randomUUID(), "tenant-north", "shared-content-hash");
        repository.save(job);

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(job);

        assertFalse(result.created());
        assertSame(job, result.canonicalJob());
    }

    @Test
    void distinctObjectCannotReplaceLiveIdentifier() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob existing = job(sharedJobId, "tenant-north", "existing-content-hash");
        ConversionJob collision = job(sharedJobId, "tenant-south", "replacement-content-hash");
        repository.save(existing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.save(collision)
        );

        assertEquals(COLLISION_MESSAGE, exception.getMessage());
        assertSame(existing, repository.findById(sharedJobId).orElseThrow());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-south",
                "replacement-content-hash"
        ).isEmpty());
    }

    @Test
    void collisionIsRejectedBeforeCandidateContentHashAccess() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob existing = job(sharedJobId, "tenant-north", "existing-content-hash");
        repository.save(existing);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(new HashAccessFailureJob(sharedJobId))
        );

        assertEquals(COLLISION_MESSAGE, exception.getMessage());
        assertSame(existing, repository.findById(sharedJobId).orElseThrow());
    }

    @Test
    void deletedIdentifierRemainsReservedAgainstLaterReplacement() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob original = job(sharedJobId, "tenant-north", "original-content-hash");
        repository.save(original);
        repository.deleteById(sharedJobId);

        ConversionJob replacement = job(sharedJobId, "tenant-south", "replacement-content-hash");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(replacement)
        );

        assertEquals(COLLISION_MESSAGE, exception.getMessage());
        assertTrue(repository.findById(sharedJobId).isEmpty());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-south",
                "replacement-content-hash"
        ).isEmpty());
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

    /** Candidate whose content hash must not be read after a UUID collision. */
    private static final class HashAccessFailureJob extends ConversionJob {

        private HashAccessFailureJob(UUID jobId) {
            super(
                    jobId,
                    "tenant-south",
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
