package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobStateStore;
import com.clearfolio.viewer.repository.ConversionJobStateStore.TenantRetryOutcome;

/**
 * Verifies that privileged mutations use atomic tenant-scoped persistence
 * operations instead of read-then-unscoped-mutate sequences.
 */
class TenantScopedAtomicMutationBoundaryTest {

    private static final TenantContext TENANT_CONTEXT = new TenantContext(
            "tenant-north",
            "operator-north",
            Set.of("admin:write")
    );

    @Test
    void tenantAwareDeleteUsesScopedRepositoryDeletionBeforeArtifactCleanup() throws Exception {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJobStateStore stateStore = mock(ConversionJobStateStore.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        UUID missingId = UUID.randomUUID();
        UUID ownedId = UUID.randomUUID();
        when(repository.deleteByTenantAndId(TENANT_CONTEXT.tenantId(), missingId))
                .thenReturn(false);
        when(repository.deleteByTenantAndId(TENANT_CONTEXT.tenantId(), ownedId))
                .thenReturn(true);
        DefaultDocumentConversionService service = service(repository, stateStore, artifactStore, mock(ConversionWorker.class));

        assertFalse(service.deleteJob(missingId, TENANT_CONTEXT));
        assertTrue(service.deleteJob(ownedId, TENANT_CONTEXT));

        verify(repository).deleteByTenantAndId(TENANT_CONTEXT.tenantId(), missingId);
        verify(repository).deleteByTenantAndId(TENANT_CONTEXT.tenantId(), ownedId);
        verify(repository, never()).findByTenantAndId(anyString(), any(UUID.class));
        verify(repository, never()).deleteById(any(UUID.class));
        verify(artifactStore, never()).deletePdf(missingId);
        verify(artifactStore).deletePdf(ownedId);
    }

    @Test
    void tenantAwareRetryMapsOneAtomicStateStoreOutcomeAndEnqueuesOnlyAcceptedJobs() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        ConversionJobStateStore stateStore = mock(ConversionJobStateStore.class);
        ConversionWorker worker = mock(ConversionWorker.class);
        UUID missingId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();
        UUID acceptedId = UUID.randomUUID();
        String actorFingerprint = "admin-v1:0123456789abcdef0123456789abcdef";
        when(stateStore.retryDeadLetteredForTenant(
                TENANT_CONTEXT.tenantId(),
                missingId,
                actorFingerprint
        )).thenReturn(TenantRetryOutcome.NOT_FOUND);
        when(stateStore.retryDeadLetteredForTenant(
                TENANT_CONTEXT.tenantId(),
                activeId,
                actorFingerprint
        )).thenReturn(TenantRetryOutcome.NOT_ELIGIBLE);
        when(stateStore.retryDeadLetteredForTenant(
                TENANT_CONTEXT.tenantId(),
                acceptedId,
                actorFingerprint
        )).thenReturn(TenantRetryOutcome.ACCEPTED);
        DefaultDocumentConversionService service = service(
                repository,
                stateStore,
                mock(ArtifactStore.class),
                worker
        );

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(missingId, TENANT_CONTEXT, actorFingerprint)
        );
        assertEquals(
                RetryDeadLetterResult.NOT_ELIGIBLE,
                service.retryDeadLettered(activeId, TENANT_CONTEXT, actorFingerprint)
        );
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(acceptedId, TENANT_CONTEXT, actorFingerprint)
        );

        verify(repository, never()).findByTenantAndId(anyString(), any(UUID.class));
        verify(stateStore, never()).retryDeadLettered(any(UUID.class), anyString());
        verify(worker, never()).enqueue(missingId);
        verify(worker, never()).enqueue(activeId);
        verify(worker).enqueue(acceptedId);
    }

    @Test
    void compatibilityStateStoreDefaultDoesNotInvokeLegacyRetry() {
        AtomicInteger legacyRetryCalls = new AtomicInteger();
        ConversionJobStateStore stateStore = new ConversionJobStateStore() {
            @Override
            public Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now) {
                return Optional.empty();
            }

            @Override
            public void scheduleRetry(UUID jobId, String message, Instant retryAt) {
            }

            @Override
            public void markSucceeded(UUID jobId, String resourcePath, String message) {
            }

            @Override
            public void markDeadLettered(UUID jobId, String message) {
            }

            @Override
            public boolean retryDeadLettered(UUID jobId, String operatorId) {
                legacyRetryCalls.incrementAndGet();
                return true;
            }
        };

        TenantRetryOutcome outcome = stateStore.retryDeadLetteredForTenant(
                TENANT_CONTEXT.tenantId(),
                UUID.randomUUID(),
                "actor"
        );

        assertEquals(TenantRetryOutcome.NOT_FOUND, outcome);
        assertEquals(0, legacyRetryCalls.get());
    }

    private static DefaultDocumentConversionService service(
            ConversionJobRepository repository,
            ConversionJobStateStore stateStore,
            ArtifactStore artifactStore,
            ConversionWorker worker
    ) {
        return new DefaultDocumentConversionService(
                repository,
                stateStore,
                mock(DocumentValidationService.class),
                worker,
                artifactStore,
                new ConversionProperties()
        );
    }
}
