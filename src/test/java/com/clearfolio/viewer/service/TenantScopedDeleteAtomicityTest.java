package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

class TenantScopedDeleteAtomicityTest {

    @Test
    void repositoryRejectsDeleteWhenCurrentOwnerChanged() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId, "tenant-a", "hash-a"));
        ConversionJob replacement = job(jobId, "tenant-b", "hash-b");
        repository.save(replacement);

        assertFalse(repository.deleteByTenantAndId("tenant-a", jobId));
        assertSame(replacement, repository.findById(jobId).orElseThrow());
    }

    @Test
    void repositoryTenantHashLookupRejectsStaleCrossTenantIndex() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId, "tenant-a", "hash-a"));
        ConversionJob replacement = job(jobId, "tenant-b", "hash-b");
        repository.save(replacement);

        assertTrue(repository.findByTenantAndContentHash("tenant-a", "hash-a").isEmpty());
        assertSame(
                replacement,
                repository.findByTenantAndContentHash("tenant-b", "hash-b").orElseThrow());
    }

    @Test
    void repositoryDeletesOnlyCurrentTenantAtomically() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        UUID jobId = UUID.randomUUID();
        repository.save(job(jobId, "tenant-a", "hash-a"));

        assertTrue(repository.deleteByTenantAndId("tenant-a", jobId));
        assertTrue(repository.findById(jobId).isEmpty());
        assertTrue(repository.findByTenantAndContentHash("tenant-a", "hash-a").isEmpty());
    }

    @Test
    void serviceDoesNotDeleteArtifactWhenAtomicTenantDeleteFails() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        DefaultDocumentConversionService service = service(repository, artifactStore);
        UUID jobId = UUID.randomUUID();
        TenantContext context = context("tenant-a");
        when(repository.deleteByTenantAndId("tenant-a", jobId)).thenReturn(false);

        assertFalse(service.deleteJob(jobId, context));

        verify(repository).deleteByTenantAndId("tenant-a", jobId);
        verify(repository, never()).deleteById(jobId);
        verify(artifactStore, never()).deletePdf(jobId);
    }

    @Test
    void serviceDeletesArtifactAfterAtomicTenantDeleteSucceeds() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        DefaultDocumentConversionService service = service(repository, artifactStore);
        UUID jobId = UUID.randomUUID();
        TenantContext context = context("tenant-a");
        when(repository.deleteByTenantAndId("tenant-a", jobId)).thenReturn(true);

        assertTrue(service.deleteJob(jobId, context));

        verify(repository).deleteByTenantAndId("tenant-a", jobId);
        verify(repository, never()).deleteById(jobId);
        verify(artifactStore).deletePdf(jobId);
    }

    private DefaultDocumentConversionService service(
            ConversionJobRepository repository,
            ArtifactStore artifactStore) {
        return new DefaultDocumentConversionService(
                repository,
                mock(DocumentValidationService.class),
                mock(ConversionWorker.class),
                artifactStore,
                mock(ConversionProperties.class));
    }

    private TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "operator-a", Set.of());
    }

    private ConversionJob job(UUID jobId, String tenantId, String contentHash) {
        return new ConversionJob(
                jobId,
                tenantId,
                "subject-a",
                "document.pdf",
                "application/pdf",
                contentHash,
                100L,
                3);
    }
}
