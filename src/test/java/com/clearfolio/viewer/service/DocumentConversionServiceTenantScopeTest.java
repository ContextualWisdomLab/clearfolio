package com.clearfolio.viewer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.model.ConversionJob;

/**
 * Regression coverage for the legacy-service tenant-scope compatibility defaults.
 */
class DocumentConversionServiceTenantScopeTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String SUBJECT_ID = "operator-a";

    /**
     * Missing tenant authority must fail closed before a job lookup or mutation.
     */
    @Test
    void retryRejectsMissingTenantAuthorityBeforeLookup() {
        LegacyService service = new LegacyService(List.of(ownedJob()));
        UUID jobId = service.jobs.getFirst().getJobId();

        RetryDeadLetterResult result = service.retryDeadLettered(jobId, SUBJECT_ID, null);

        assertThat(result).isEqualTo(RetryDeadLetterResult.NOT_FOUND);
        assertThat(service.getJobCalls).isZero();
        assertThat(service.legacyRetryCalls).isZero();
    }

    /**
     * Missing and foreign jobs must be concealed without invoking the legacy mutation.
     */
    @Test
    void retryConcealsMissingAndForeignJobs() {
        LegacyService missingService = new LegacyService(List.of());
        UUID missingJobId = UUID.randomUUID();

        assertThat(missingService.retryDeadLettered(missingJobId, SUBJECT_ID, tenantContext()))
                .isEqualTo(RetryDeadLetterResult.NOT_FOUND);
        assertThat(missingService.legacyRetryCalls).isZero();

        ConversionJob foreignJob = job("tenant-b", "foreign.pdf");
        LegacyService foreignService = new LegacyService(List.of(foreignJob));

        assertThat(foreignService.retryDeadLettered(
                foreignJob.getJobId(),
                SUBJECT_ID,
                tenantContext()
        )).isEqualTo(RetryDeadLetterResult.NOT_FOUND);
        assertThat(foreignService.legacyRetryCalls).isZero();
    }

    /**
     * An owned job may delegate to the legacy retry using the authenticated operator identity.
     */
    @Test
    void retryDelegatesOwnedJobWithAuthenticatedOperator() {
        ConversionJob ownedJob = ownedJob();
        LegacyService service = new LegacyService(List.of(ownedJob));

        RetryDeadLetterResult result = service.retryDeadLettered(
                ownedJob.getJobId(),
                SUBJECT_ID,
                tenantContext()
        );

        assertThat(result).isEqualTo(RetryDeadLetterResult.ACCEPTED);
        assertThat(service.legacyRetryCalls).isEqualTo(1);
        assertThat(service.lastRetriedJobId).isEqualTo(ownedJob.getJobId());
        assertThat(service.lastOperatorId).isEqualTo(SUBJECT_ID);
    }

    /**
     * Missing tenant authority must return an empty snapshot without reading the global inventory.
     */
    @Test
    void listRejectsMissingTenantAuthorityWithoutGlobalRead() {
        LegacyService service = new LegacyService(List.of(ownedJob()));

        assertThat(service.getAllJobs(null)).isEmpty();
        assertThat(service.getAllJobsCalls).isZero();
    }

    /**
     * The compatibility list filters the legacy global inventory at the tenant boundary.
     */
    @Test
    void listReturnsOnlyOwnedJobs() {
        ConversionJob firstOwned = ownedJob();
        ConversionJob foreign = job("tenant-b", "foreign.pdf");
        ConversionJob secondOwned = job(TENANT_ID, "second.pdf");
        LegacyService service = new LegacyService(List.of(firstOwned, foreign, secondOwned));

        assertThat(service.getAllJobs(tenantContext()))
                .containsExactly(firstOwned, secondOwned);
        assertThat(service.getAllJobsCalls).isEqualTo(1);
    }

    private static TenantContext tenantContext() {
        return new TenantContext(TENANT_ID, SUBJECT_ID, Set.of("tenant:configure"));
    }

    private static ConversionJob ownedJob() {
        return job(TENANT_ID, "owned.pdf");
    }

    private static ConversionJob job(String tenantId, String fileName) {
        return new ConversionJob(
                UUID.randomUUID(),
                tenantId,
                SUBJECT_ID,
                fileName,
                "application/pdf",
                "hash-" + fileName,
                100L,
                3
        );
    }

    /**
     * Minimal legacy implementation used to observe default-method delegation.
     */
    private static final class LegacyService implements DocumentConversionService {

        private final List<ConversionJob> jobs;
        private int getJobCalls;
        private int getAllJobsCalls;
        private int legacyRetryCalls;
        private UUID lastRetriedJobId;
        private String lastOperatorId;

        LegacyService(List<ConversionJob> jobs) {
            this.jobs = new ArrayList<>(jobs);
        }

        @Override
        public UUID submit(MultipartFile file) {
            throw new UnsupportedOperationException("submission is outside this test seam");
        }

        @Override
        public Optional<ConversionJob> getJob(UUID jobId) {
            getJobCalls += 1;
            return jobs.stream()
                    .filter(job -> job.getJobId().equals(jobId))
                    .findFirst();
        }

        @Override
        public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
            legacyRetryCalls += 1;
            lastRetriedJobId = jobId;
            lastOperatorId = operatorId;
            return RetryDeadLetterResult.ACCEPTED;
        }

        @Override
        public void deleteJob(UUID jobId) {
            jobs.removeIf(job -> job.getJobId().equals(jobId));
        }

        @Override
        public Iterable<ConversionJob> getAllJobs() {
            getAllJobsCalls += 1;
            return List.copyOf(jobs);
        }
    }
}
