package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

class DocumentConversionServiceTenantScopeDefaultTest {

    @Test
    void scopedInventoryReturnsOnlyOwnedJobsAndFailsClosedWithoutAuthority() {
        ConversionJob tenantAJob = tenantJob("tenant-a", "hash-a");
        ConversionJob tenantBJob = tenantJob("tenant-b", "hash-b");
        StubConversionService service = new StubConversionService(
                List.of(tenantAJob, tenantBJob)
        );

        assertEquals(List.of(), toList(service.getAllJobs(null)));
        assertEquals(
                List.of(tenantAJob),
                toList(service.getAllJobs(context("tenant-a")))
        );
    }

    @Test
    void scopedRetryConcealsMissingAndForeignJobsBeforeLegacyMutation() {
        ConversionJob tenantAJob = tenantJob("tenant-a", "hash-a");
        ConversionJob tenantBJob = tenantJob("tenant-b", "hash-b");
        StubConversionService service = new StubConversionService(
                List.of(tenantAJob, tenantBJob)
        );

        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(tenantAJob.getJobId(), "operator", null)
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(UUID.randomUUID(), "operator", context("tenant-a"))
        );
        assertEquals(
                RetryDeadLetterResult.NOT_FOUND,
                service.retryDeadLettered(tenantBJob.getJobId(), "operator", context("tenant-a"))
        );
        assertEquals(
                RetryDeadLetterResult.ACCEPTED,
                service.retryDeadLettered(tenantAJob.getJobId(), "operator", context("tenant-a"))
        );
        assertEquals(1, service.retryCalls);
        assertEquals(tenantAJob.getJobId(), service.lastRetriedJobId);
        assertEquals("operator", service.lastOperatorId);
    }

    private static List<ConversionJob> toList(Iterable<ConversionJob> jobs) {
        List<ConversionJob> result = new ArrayList<>();
        jobs.forEach(result::add);
        return result;
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "operator", Set.of("tenant:configure"));
    }

    private static ConversionJob tenantJob(String tenantId, String contentHash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "operator",
                "document.pdf",
                "application/pdf",
                contentHash,
                100L,
                3
        );
    }

    private static final class StubConversionService implements DocumentConversionService {

        private final List<ConversionJob> jobs;
        private int retryCalls;
        private UUID lastRetriedJobId;
        private String lastOperatorId;

        private StubConversionService(List<ConversionJob> jobs) {
            this.jobs = jobs;
        }

        @Override
        public UUID submit(MultipartFile file) {
            return UUID.randomUUID();
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            return jobs.stream()
                    .filter(job -> job.getJobId().equals(jobId))
                    .findFirst();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            retryCalls++;
            lastRetriedJobId = jobId;
            lastOperatorId = operatorId;
            return RetryDeadLetterResult.ACCEPTED;
        }

        @Override
        public void deleteJob(UUID jobId) {
            // Not used by these default-method contract tests.
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return jobs;
        }
    }
}
