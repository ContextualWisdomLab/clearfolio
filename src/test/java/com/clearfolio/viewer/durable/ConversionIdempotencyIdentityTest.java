package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConversionIdempotencyIdentityTest {

    private static final String SOURCE_DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void canonicalKeyIsStableForSameAcceptanceIdentity() {
        ConversionIdempotencyIdentity first = identity(
                "tenant-a",
                SOURCE_DIGEST,
                "office-policy-v4",
                "pdf-output-v2"
        );
        ConversionIdempotencyIdentity second = identity(
                "tenant-a",
                SOURCE_DIGEST,
                "office-policy-v4",
                "pdf-output-v2"
        );

        assertEquals(first, second);
        assertEquals(first.canonicalKey(), second.canonicalKey());
        assertEquals(64, first.canonicalKey().length());
    }

    @Test
    void canonicalKeyBindsEveryRequiredAcceptanceDimension() {
        ConversionIdempotencyIdentity baseline = identity(
                "tenant-a",
                SOURCE_DIGEST,
                "office-policy-v4",
                "pdf-output-v2"
        );

        assertNotEquals(
                baseline.canonicalKey(),
                identity("tenant-b", SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2").canonicalKey()
        );
        assertNotEquals(
                baseline.canonicalKey(),
                identity("tenant-a", "f".repeat(64), "office-policy-v4", "pdf-output-v2").canonicalKey()
        );
        assertNotEquals(
                baseline.canonicalKey(),
                identity("tenant-a", SOURCE_DIGEST, "office-policy-v5", "pdf-output-v2").canonicalKey()
        );
        assertNotEquals(
                baseline.canonicalKey(),
                identity("tenant-a", SOURCE_DIGEST, "office-policy-v4", "pdf-output-v3").canonicalKey()
        );
    }

    @Test
    void lengthPrefixedHashingDoesNotCollapseDelimiterLikeValues() {
        ConversionIdempotencyIdentity first = identity(
                "tenant|a",
                SOURCE_DIGEST,
                "policy|v1",
                "pdf-output-v2"
        );
        ConversionIdempotencyIdentity second = identity(
                "tenant",
                SOURCE_DIGEST,
                "a|policy|v1",
                "pdf-output-v2"
        );

        assertNotEquals(first.canonicalKey(), second.canonicalKey());
    }

    @Test
    void rejectsNonCanonicalSourceDigest() {
        assertThrows(NullPointerException.class, () -> identity(
                "tenant-a", null, "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", "", "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", "A".repeat(64), "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", " " + SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", "0".repeat(63), "office-policy-v4", "pdf-output-v2"
        ));
    }

    @Test
    void rejectsMissingOrNonCanonicalTextAuthority() {
        assertThrows(NullPointerException.class, () -> identity(
                null, SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                " ", SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                " tenant-a", SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", SOURCE_DIGEST, "office-policy-v4 ", "pdf-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "tenant-a", SOURCE_DIGEST, "office-policy-v4", "pdf\u0000-output-v2"
        ));
        assertThrows(IllegalArgumentException.class, () -> identity(
                "x".repeat(257), SOURCE_DIGEST, "office-policy-v4", "pdf-output-v2"
        ));
    }

    private static ConversionIdempotencyIdentity identity(
            String tenantId,
            String sourceDigest,
            String conversionPolicyVersion,
            String outputContractVersion
    ) {
        return new ConversionIdempotencyIdentity(
                tenantId,
                sourceDigest,
                conversionPolicyVersion,
                outputContractVersion
        );
    }
}
