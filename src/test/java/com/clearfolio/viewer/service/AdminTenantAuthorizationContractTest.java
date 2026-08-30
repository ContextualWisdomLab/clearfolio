package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

class AdminTenantAuthorizationContractTest {

    private static final TenantContext ADMIN_CONTEXT = new TenantContext(
            "tenant-a",
            "admin-a",
            Set.of(TenantPermissions.ADMIN_READ, TenantPermissions.ADMIN_WRITE)
    );

    @Test
    void adminListIsTenantScopedAtServiceBoundary() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        repository.save(jobFor("tenant-a", "owned.pdf", "owned-hash"));
        repository.save(jobFor("tenant-b", "foreign.pdf", "foreign-hash"));
        DocumentConversionService service = service(repository, new AtomicInteger());

        List<ConversionJob> visible = toList(service.getAllJobs(ADMIN_CONTEXT));

        assertEquals(1, visible.size());
        assertEquals("tenant-a", visible.get(0).getTenantId());
        assertEquals("owned.pdf", visible.get(0).getOriginalFileName());
    }

    @Test
    void adminRetryHidesForeignJobAndUsesVerifiedSubjectForOwnedJob() {
        InMemoryConversionJobRepository repository = new InMemoryConversionJobRepository();
        AtomicInteger enqueued = new AtomicInteger();
        DocumentConversionService service = service(repository, enqueued);

        ConversionJob foreign = deadLetteredJob("tenant-b", "foreign.pdf", "foreign-hash");
        repository.save(foreign);
        assertEquals(RetryDeadLetterResult.NOT_FOUND, service.retryDeadLettered(foreign.getJobId(), ADMIN_CONTEXT));
        assertTrue(foreign.isDeadLettered());
        assertEquals(0, enqueued.get());

        ConversionJob owned = deadLetteredJob("tenant-a", "owned.pdf", "owned-hash");
        repository.save(owned);
        assertEquals(RetryDeadLetterResult.ACCEPTED, service.retryDeadLettered(owned.getJobId(), ADMIN_CONTEXT));
        assertEquals(ConversionJobStatus.SUBMITTED, owned.getStatus());
        assertTrue(owned.getStatusMessage().contains("admin-a"));
        assertEquals(1, enqueued.get());
    }

    private static DocumentConversionService service(
            InMemoryConversionJobRepository repository,
            AtomicInteger enqueued) {
        return new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                jobId -> enqueued.incrementAndGet(),
                new ConversionProperties()
        );
    }

    private static ConversionJob jobFor(String tenantId, String fileName, String hash) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                "submitter",
                fileName,
                "application/pdf",
                hash,
                100L,
                3
        );
    }

    private static ConversionJob deadLetteredJob(String tenantId, String fileName, String hash) {
        ConversionJob job = jobFor(tenantId, fileName, hash);
        assertTrue(job.markProcessing("first attempt"));
        job.markDeadLettered("retries exhausted");
        return job;
    }

    private static List<ConversionJob> toList(Iterable<ConversionJob> jobs) {
        java.util.ArrayList<ConversionJob> copy = new java.util.ArrayList<>();
        jobs.forEach(copy::add);
        return List.copyOf(copy);
    }
}
