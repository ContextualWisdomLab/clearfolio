package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

class DocumentConversionServiceTenantIsolationTest {

    private static final TenantContext TENANT_A = new TenantContext(
            "tenant-a", "admin-a", Set.of("admin:read", "admin:write"));
    private static final TenantContext TENANT_B = new TenantContext(
            "tenant-b", "admin-b", Set.of("admin:read", "admin:write"));

    @Test
    void tenantScopedListExcludesForeignJobs() {
        FakeConversionService service = new FakeConversionService();
        ConversionJob owned = job("tenant-a", "owned.pdf");
        ConversionJob foreign = job("tenant-b", "foreign.pdf");
        service.jobs.add(owned);
        service.jobs.add(foreign);

        List<ConversionJob> visible = new ArrayList<>();
        service.getAllJobs(TENANT_A).forEach(visible::add);

        assertEquals(List.of(owned), visible);
    }

    @Test
    void tenantScopedDeleteDoesNotMutateForeignJob() {
        FakeConversionService service = new FakeConversionService();
        ConversionJob foreign = job("tenant-b", "foreign.pdf");
        service.jobs.add(foreign);

        boolean deleted = service.deleteJob(foreign.getJobId(), TENANT_A);

        assertFalse(deleted);
        assertEquals(0, service.deleteInvocations);
        assertTrue(service.getJob(foreign.getJobId()).isPresent());
    }

    @Test
    void tenantScopedRetryDoesNotMutateForeignJob() {
        FakeConversionService service = new FakeConversionService();
        ConversionJob foreign = job("tenant-b", "foreign.pdf");
        service.jobs.add(foreign);

        RetryDeadLetterResult result = service.retryDeadLettered(
                foreign.getJobId(), TENANT_A.subjectId(), TENANT_A);

        assertEquals(RetryDeadLetterResult.NOT_FOUND, result);
        assertEquals(0, service.retryInvocations);
    }

    @Test
    void tenantScopedMutationsDelegateForOwnedJob() {
        FakeConversionService service = new FakeConversionService();
        ConversionJob retryJob = job("tenant-a", "retry.pdf");
        ConversionJob deleteJob = job("tenant-a", "delete.pdf");
        service.jobs.add(retryJob);
        service.jobs.add(deleteJob);

        RetryDeadLetterResult retryResult = service.retryDeadLettered(
                retryJob.getJobId(), TENANT_A.subjectId(), TENANT_A);
        boolean deleted = service.deleteJob(deleteJob.getJobId(), TENANT_A);

        assertEquals(RetryDeadLetterResult.ACCEPTED, retryResult);
        assertEquals(1, service.retryInvocations);
        assertTrue(deleted);
        assertEquals(1, service.deleteInvocations);
        assertTrue(service.getJob(deleteJob.getJobId()).isEmpty());
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "subject",
                fileName,
                "application/pdf",
                "hash-" + UUID.randomUUID(),
                100L,
                3);
    }

    private static final class FakeConversionService implements DocumentConversionService {
        private final List<ConversionJob> jobs = new ArrayList<>();
        private int deleteInvocations;
        private int retryInvocations;

        @Override
        public UUID submit(MultipartFile file) {
            throw new UnsupportedOperationException("not used by tenant-isolation tests");
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            return jobs.stream().filter(job -> job.getJobId().equals(jobId)).findFirst();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            retryInvocations++;
            return getJob(jobId).isPresent()
                    ? RetryDeadLetterResult.ACCEPTED
                    : RetryDeadLetterResult.NOT_FOUND;
        }

        @Override
        public void deleteJob(UUID jobId) {
            deleteInvocations++;
            jobs.removeIf(job -> job.getJobId().equals(jobId));
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            return List.copyOf(jobs);
        }
    }
}
