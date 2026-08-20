package com.clearfolio.viewer.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionWorkerLeaseTest {

    @Test
    void leaseCarriesImmutableJobGenerationAndLeaseIdentity() {
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        ConversionWorkerLease lease = ConversionWorkerLease.issue(jobId, 7L, leaseId);

        assertEquals(jobId, lease.jobId());
        assertEquals(7L, lease.generation());
        assertEquals(leaseId, lease.leaseId());
    }

    @Test
    void authorizationRequiresExactJobGenerationAndLeaseIdentity() {
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        ConversionWorkerLease lease = ConversionWorkerLease.issue(jobId, 2L, leaseId);

        assertTrue(lease.authorizes(jobId, 2L, leaseId));
        assertFalse(lease.authorizes(UUID.randomUUID(), 2L, leaseId));
        assertFalse(lease.authorizes(jobId, 3L, leaseId));
        assertFalse(lease.authorizes(jobId, 2L, UUID.randomUUID()));
        assertFalse(lease.authorizes(null, 2L, leaseId));
        assertFalse(lease.authorizes(jobId, 2L, null));
    }

    @Test
    void leaseIssuanceFailsClosedForMissingOrNonPositiveAuthority() {
        UUID jobId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> ConversionWorkerLease.issue(null, 1L, leaseId));
        assertThrows(NullPointerException.class, () -> ConversionWorkerLease.issue(jobId, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> ConversionWorkerLease.issue(jobId, 0L, leaseId));
        assertThrows(IllegalArgumentException.class, () -> ConversionWorkerLease.issue(jobId, -1L, leaseId));
    }
}
