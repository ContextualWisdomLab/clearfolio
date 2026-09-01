package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Architecture and compatibility regressions for tenant-scoped application
 * service contracts used by external administration adapters.
 */
class DocumentConversionServiceTenantBoundaryTest {

    @Test
    void exposesTenantScopedListAndRetryContracts() {
        assertDoesNotThrow(
                () -> DocumentConversionService.class.getMethod(
                        "getJobsForTenant",
                        TenantContext.class
                ),
                "admin list behavior must not require an HTTP adapter to fetch the global job set"
        );
        assertDoesNotThrow(
                () -> DocumentConversionService.class.getMethod(
                        "retryDeadLettered",
                        UUID.class,
                        String.class,
                        TenantContext.class
                ),
                "retry ownership must be enforced by the application-service boundary"
        );
    }

    @Test
    void compatibilityListDefaultFiltersTenantAndFailsClosedWithoutContext() {
        ConversionJob tenantA = job("tenant-a", "a.pdf");
        ConversionJob tenantB = job("tenant-b", "b.pdf");
        DefaultBoundaryService service = new DefaultBoundaryService(List.of(tenantA, tenantB));

        List<ConversionJob> visible = toList(service.getJobsForTenant(context("tenant-a")));

        assertEquals(List.of(tenantA), visible);
        assertTrue(toList(service.getJobsForTenant(null)).isEmpty());
    }

    @Test
    void compatibilityRetryDefaultHidesMissingAndForeignResources() {
        ConversionJob tenantB = job("tenant-b", "b.pdf");
        DefaultBoundaryService service = new DefaultBoundaryService(List.of(tenantB));

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(tenantB.getJobId(), "v1:audit", null)
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(UUID.randomUUID(), "v1:audit", context("tenant-a"))
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(tenantB.getJobId(), "v1:audit", context("tenant-a"))
        );
        assertEquals(0, service.unscopedRetryCalls);
    }

    @Test
    void compatibilityRetryDefaultDelegatesOnlyForOwnedResource() {
        ConversionJob tenantA = job("tenant-a", "a.pdf");
        DefaultBoundaryService service = new DefaultBoundaryService(List.of(tenantA));

        RetryDeadLetterResult result = service.retryDeadLettered(
                tenantA.getJobId(),
                "v1:audit",
                context("tenant-a")
        );

        assertEquals(RetryDeadLetterResult.ACCEPTED, result);
        assertEquals(1, service.unscopedRetryCalls);
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "subject", Set.of());
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                "hash-" + tenantId,
                10L,
                3
        );
    }

    private static List<ConversionJob> toList(Iterable<ConversionJob> jobs) {
        java.util.ArrayList<ConversionJob> result = new java.util.ArrayList<>();
        jobs.forEach(result::add);
        return result;
    }

    private static final class DefaultBoundaryService implements DocumentConversionService {
        private final List<ConversionJob> jobs;
        private int unscopedRetryCalls;

        private DefaultBoundaryService(List<ConversionJob> jobs) {
            this.jobs = jobs;
        }

        @Override
        public UUID submit(MultipartFile file) {
            return UUID.randomUUID();
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            return jobs.stream().filter(job -> job.getJobId().equals(jobId)).findFirst();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            unscopedRetryCalls++;
            return RetryDeadLetterResult.ACCEPTED;
        }

        @Override
        public void deleteJob(UUID jobId) {
            // Not used by this compatibility-boundary test double.
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return jobs;
        }
    }
}
