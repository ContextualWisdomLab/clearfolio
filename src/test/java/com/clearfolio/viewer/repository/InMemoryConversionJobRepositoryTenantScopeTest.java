package com.clearfolio.viewer.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.ConversionJobStateStore.TenantRetryOutcome;

/**
 * Proves that administrative operations are bounded at the repository layer
 * before another tenant's job objects or mutations cross the service boundary.
 */
class InMemoryConversionJobRepositoryTenantScopeTest {

    @Test
    void returnsOnlyJobsOwnedByTheRequestedTenant() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob northFirst = job("tenant-north", "north-first.pdf");
        ConversionJob northSecond = job("tenant-north", "north-second.pdf");
        ConversionJob southSecret = job("tenant-south", "south-secret.pdf");
        repository.save(northFirst);
        repository.save(southSecret);
        repository.save(northSecond);

        List<ConversionJob> tenantJobs = repository.findAllByTenantId("tenant-north");

        assertEquals(2, tenantJobs.size());
        assertEquals(
                Set.of(northFirst.getJobId(), northSecond.getJobId()),
                tenantJobs.stream().map(ConversionJob::getJobId).collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(tenantJobs.stream().noneMatch(job -> "tenant-south".equals(job.getTenantId())));
    }

    @Test
    void nullOrBlankTenantIdentifiersFailClosed() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(job("tenant-north", "north.pdf"));

        assertTrue(repository.findAllByTenantId(null).isEmpty());
        assertTrue(repository.findAllByTenantId("   ").isEmpty());
        assertFalse(repository.deleteByTenantAndId(null, UUID.randomUUID()));
        assertFalse(repository.deleteByTenantAndId("   ", UUID.randomUUID()));
        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant(null, UUID.randomUUID(), "actor")
        );
        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant("   ", UUID.randomUUID(), "actor")
        );
    }

    @Test
    void saveRejectsCrossTenantIdentifierReplacementWithoutChangingIndexes() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob currentNorth = job(
                sharedJobId,
                "tenant-north",
                "north-current.pdf",
                "north-content-hash"
        );
        ConversionJob replacementSouth = job(
                sharedJobId,
                "tenant-south",
                "south-replacement.pdf",
                "south-content-hash"
        );
        repository.save(currentNorth);

        assertSame(currentNorth, repository.save(currentNorth));
        assertThrows(IllegalStateException.class, () -> repository.save(replacementSouth));
        assertSame(currentNorth, repository.findById(sharedJobId).orElseThrow());
        assertSame(
                currentNorth,
                repository.findByTenantAndContentHash(
                        "tenant-north",
                        currentNorth.getContentHash()
                ).orElseThrow()
        );
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-south",
                replacementSouth.getContentHash()
        ).isEmpty());
    }

    @Test
    void deletedIdentifierRemainsReservedForAtomicFindOrStore() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob originalNorth = job(
                sharedJobId,
                "tenant-north",
                "north-original.pdf",
                "north-original-hash"
        );
        ConversionJob replacementSouth = job(
                sharedJobId,
                "tenant-south",
                "south-replacement.pdf",
                "south-replacement-hash"
        );
        repository.save(originalNorth);

        assertTrue(repository.deleteByTenantAndId("tenant-north", sharedJobId));
        assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(replacementSouth)
        );
        assertTrue(repository.findById(sharedJobId).isEmpty());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-south",
                replacementSouth.getContentHash()
        ).isEmpty());
    }

    @Test
    void findOrStoreRejectsAJobIdentifierCollisionWithoutChangingOwnership() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob currentSouth = job(
                sharedJobId,
                "tenant-south",
                "south-current.pdf",
                "south-current-hash"
        );
        ConversionJob collidingNorth = job(
                sharedJobId,
                "tenant-north",
                "north-collision.pdf",
                "north-collision-hash"
        );
        repository.save(currentSouth);

        assertThrows(
                IllegalStateException.class,
                () -> repository.findOrStoreByContentHash(collidingNorth)
        );
        assertSame(currentSouth, repository.findById(sharedJobId).orElseThrow());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-north",
                collidingNorth.getContentHash()
        ).isEmpty());
    }

    @Test
    void rejectedCrossTenantReplacementCannotTransferDeleteAuthority() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob currentNorth = job(
                sharedJobId,
                "tenant-north",
                "north-current.pdf",
                "north-current-hash"
        );
        ConversionJob replacementSouth = job(
                sharedJobId,
                "tenant-south",
                "south-replacement.pdf",
                "south-replacement-hash"
        );
        repository.save(currentNorth);

        assertThrows(IllegalStateException.class, () -> repository.save(replacementSouth));
        assertFalse(repository.deleteByTenantAndId("tenant-south", sharedJobId));
        assertSame(currentNorth, repository.findById(sharedJobId).orElseThrow());
        assertTrue(repository.deleteByTenantAndId("tenant-north", sharedJobId));
        assertTrue(repository.findById(sharedJobId).isEmpty());
    }

    @Test
    void tenantScopedDeleteConcealsMissingAndCrossTenantJobsThenRemovesOwnedIndex() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob north = job("tenant-north", "north.pdf");
        ConversionJob south = job("tenant-south", "south.pdf");
        repository.save(north);
        repository.save(south);

        assertFalse(repository.deleteByTenantAndId("tenant-north", UUID.randomUUID()));
        assertFalse(repository.deleteByTenantAndId("tenant-north", south.getJobId()));
        assertTrue(repository.findById(south.getJobId()).isPresent());
        assertTrue(repository.deleteByTenantAndId(" tenant-north ", north.getJobId()));
        assertTrue(repository.findById(north.getJobId()).isEmpty());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-north",
                north.getContentHash()
        ).isEmpty());
    }

    @Test
    void tenantScopedDeleteHandlesJobsWithoutIndexedContentHashes() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob nullHash = job("tenant-north", "null-hash.pdf", null);
        ConversionJob blankHash = job("tenant-north", "blank-hash.pdf", "   ");
        repository.save(nullHash);
        repository.save(blankHash);

        assertTrue(repository.deleteByTenantAndId("tenant-north", nullHash.getJobId()));
        assertTrue(repository.deleteByTenantAndId("tenant-north", blankHash.getJobId()));
        repository.deleteById(UUID.randomUUID());
    }

    @Test
    void rejectedCrossTenantReplacementCannotTransferRetryAuthority() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID sharedJobId = UUID.randomUUID();
        ConversionJob currentNorth = deadLetteredJob(
                sharedJobId,
                "tenant-north",
                "north-current.pdf",
                "north-current-retry-hash"
        );
        ConversionJob replacementSouth = deadLetteredJob(
                sharedJobId,
                "tenant-south",
                "south-replacement.pdf",
                "south-replacement-retry-hash"
        );
        repository.save(currentNorth);

        assertThrows(IllegalStateException.class, () -> repository.save(replacementSouth));
        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant(
                        "tenant-south",
                        sharedJobId,
                        "actor-south"
                )
        );
        assertEquals(
                TenantRetryOutcome.ACCEPTED,
                repository.retryDeadLetteredForTenant(
                        "tenant-north",
                        sharedJobId,
                        "actor-north"
                )
        );
        assertSame(currentNorth, repository.findById(sharedJobId).orElseThrow());
        assertEquals(ConversionJobStatus.SUBMITTED, currentNorth.getStatus());
        assertTrue(repository.findByTenantAndContentHash(
                "tenant-south",
                replacementSouth.getContentHash()
        ).isEmpty());
    }

    @Test
    void tenantScopedRetryAtomicallyConcealsOwnershipAndMapsEligibility() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionJob active = job("tenant-north", "active.pdf");
        ConversionJob retryable = deadLetteredJob("tenant-north", "retryable.pdf");
        ConversionJob south = deadLetteredJob("tenant-south", "south-secret.pdf");
        repository.save(active);
        repository.save(retryable);
        repository.save(south);

        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant(
                        "tenant-north",
                        UUID.randomUUID(),
                        "actor-missing"
                )
        );
        assertEquals(
                TenantRetryOutcome.NOT_FOUND,
                repository.retryDeadLetteredForTenant(
                        "tenant-north",
                        south.getJobId(),
                        "actor-cross-tenant"
                )
        );
        assertEquals(
                TenantRetryOutcome.NOT_ELIGIBLE,
                repository.retryDeadLetteredForTenant(
                        "tenant-north",
                        active.getJobId(),
                        "actor-active"
                )
        );
        assertEquals(
                TenantRetryOutcome.ACCEPTED,
                repository.retryDeadLetteredForTenant(
                        " tenant-north ",
                        retryable.getJobId(),
                        "actor-owned"
                )
        );
        assertEquals(ConversionJobStatus.SUBMITTED, retryable.getStatus());
        assertTrue(retryable.getStatusMessage().contains("actor-owned"));
        assertEquals(
                1,
                repository.findLifecycleEventsByJobId(retryable.getJobId()).stream()
                        .filter(event -> "conversion.retry.accepted".equals(event.eventType()))
                        .count()
        );
    }

    private static ConversionJob deadLetteredJob(String tenantId, String fileName) {
        return deadLetteredJob(
                UUID.randomUUID(),
                tenantId,
                fileName,
                UUID.randomUUID().toString()
        );
    }

    private static ConversionJob deadLetteredJob(
            UUID jobId,
            String tenantId,
            String fileName,
            String contentHash
    ) {
        ConversionJob job = job(jobId, tenantId, fileName, contentHash);
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        return job;
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return job(tenantId, fileName, UUID.randomUUID().toString());
    }

    private static ConversionJob job(String tenantId, String fileName, String contentHash) {
        return job(UUID.randomUUID(), tenantId, fileName, contentHash);
    }

    private static ConversionJob job(
            UUID jobId,
            String tenantId,
            String fileName,
            String contentHash
    ) {
        return new ConversionJob(
                jobId,
                tenantId,
                "owner",
                fileName,
                "application/pdf",
                contentHash,
                100L,
                3
        );
    }
}
