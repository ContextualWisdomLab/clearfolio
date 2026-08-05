package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Verifies that compatibility-only service adapters cannot perform privileged
 * mutations through unscoped legacy methods.
 *
 * <p>Tenant-aware interface defaults are security boundaries. An adapter that
 * implements only the historical global methods must fail closed until it
 * supplies an atomic tenant-scoped mutation implementation.</p>
 */
class TenantScopedServiceDefaultsTest {

    private static final TenantContext TENANT_CONTEXT = new TenantContext(
            "tenant-north",
            "operator-north",
            Set.of("admin:write")
    );

    @Test
    void tenantAwareDeleteDefaultDoesNotReadOrInvokeLegacyMutation() {
        CompatibilityOnlyService service = new CompatibilityOnlyService();

        boolean deleted = service.deleteJob(UUID.randomUUID(), TENANT_CONTEXT);

        assertFalse(deleted);
        assertEquals(0, service.getJobCalls);
        assertEquals(0, service.legacyDeleteCalls);
    }

    @Test
    void tenantAwareRetryDefaultDoesNotReadOrInvokeLegacyMutation() {
        CompatibilityOnlyService service = new CompatibilityOnlyService();

        RetryDeadLetterResult result = service.retryDeadLettered(
                UUID.randomUUID(),
                TENANT_CONTEXT,
                "admin-v1:0123456789abcdef0123456789abcdef"
        );

        assertEquals(RetryDeadLetterResult.NOT_FOUND, result);
        assertEquals(0, service.getJobCalls);
        assertEquals(0, service.legacyRetryCalls);
    }

    private static final class CompatibilityOnlyService implements DocumentConversionService {

        private int getJobCalls;
        private int legacyDeleteCalls;
        private int legacyRetryCalls;

        @Override
        public UUID submit(MultipartFile file) {
            throw new UnsupportedOperationException("submission is outside this contract test");
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            getJobCalls += 1;
            return Optional.empty();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            legacyRetryCalls += 1;
            return RetryDeadLetterResult.ACCEPTED;
        }

        @Override
        public void deleteJob(UUID jobId) {
            legacyDeleteCalls += 1;
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return List.of();
        }
    }
}
