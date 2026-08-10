package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Defines the immutable, tenant-bound identity carried by future durable deletion receipts.
 */
class ArtifactDeletionReceiptIdentityTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String CHECKSUM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void normalizesBoundedTextWithoutChangingCryptographicDigest() {
        ArtifactDeletionReceiptIdentity identity = new ArtifactDeletionReceiptIdentity(
                REQUEST_ID,
                "  tenant-example  ",
                JOB_ID,
                CHECKSUM,
                "  audit-v1:0123456789abcdef  ",
                REQUESTED_AT
        );

        assertEquals(REQUEST_ID, identity.requestId());
        assertEquals("tenant-example", identity.tenantId());
        assertEquals(JOB_ID, identity.jobId());
        assertEquals(CHECKSUM, identity.artifactChecksum());
        assertEquals("audit-v1:0123456789abcdef", identity.auditCorrelationId());
        assertEquals(REQUESTED_AT, identity.requestedAt());
    }

    @Test
    void rejectsMissingImmutableIdentifiersAndTime() {
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactDeletionReceiptIdentity(
                        null, "tenant-example", JOB_ID, CHECKSUM, "audit-v1:abc", REQUESTED_AT));
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactDeletionReceiptIdentity(
                        REQUEST_ID, "tenant-example", null, CHECKSUM, "audit-v1:abc", REQUESTED_AT));
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactDeletionReceiptIdentity(
                        REQUEST_ID, "tenant-example", JOB_ID, CHECKSUM, "audit-v1:abc", null));
    }

    @Test
    void rejectsBlankOrOversizedTenantAndAuditIdentifiers() {
        assertEquals(
                "tenantId must not be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, " \t ", JOB_ID, CHECKSUM, "audit-v1:abc", REQUESTED_AT))
                        .getMessage());
        assertEquals(
                "auditCorrelationId must not be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "tenant-example", JOB_ID, CHECKSUM, "  ", REQUESTED_AT))
                        .getMessage());
        assertEquals(
                "tenantId exceeds the configured bound",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "t".repeat(257), JOB_ID, CHECKSUM, "audit-v1:abc", REQUESTED_AT))
                        .getMessage());
        assertEquals(
                "auditCorrelationId exceeds the configured bound",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "tenant-example", JOB_ID, CHECKSUM, "a".repeat(257), REQUESTED_AT))
                        .getMessage());
    }

    @Test
    void rejectsMissingOrNonCanonicalArtifactDigest() {
        assertEquals(
                "artifactChecksum must not be blank",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "tenant-example", JOB_ID, " ", "audit-v1:abc", REQUESTED_AT))
                        .getMessage());
        assertEquals(
                "artifactChecksum must be a lowercase SHA-256 digest",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "tenant-example", JOB_ID, CHECKSUM.toUpperCase(), "audit-v1:abc", REQUESTED_AT))
                        .getMessage());
        assertEquals(
                "artifactChecksum must be a lowercase SHA-256 digest",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ArtifactDeletionReceiptIdentity(
                                REQUEST_ID, "tenant-example", JOB_ID, "0".repeat(63), "audit-v1:abc", REQUESTED_AT))
                        .getMessage());
    }
}
