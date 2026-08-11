package com.clearfolio.viewer.durable;

import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK;
import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.sha256ProviderPositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Security;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

@ResourceLock("java.security.Security.providers")
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

    @Test
    void canonicalKeyFailsClosedWhenSha256ProviderIsUnavailable() {
        ConversionIdempotencyIdentity identity = identity(
                "tenant-a",
                SOURCE_DIGEST,
                "office-policy-v4",
                "pdf-output-v2"
        );

        synchronized (SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> providers = sha256ProviderPositions();
            assertFalse(providers.isEmpty());
            providers.forEach(position -> Security.removeProvider(position.provider().getName()));
            try {
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        identity::canonicalKey
                );
                assertEquals("SHA-256 digest unavailable", exception.getMessage());
            } finally {
                providers.stream()
                        .sorted(Comparator.comparingInt(ProviderPosition::position))
                        .forEach(position -> Security.insertProviderAt(
                                position.provider(),
                                position.position()
                        ));
            }
        }
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
