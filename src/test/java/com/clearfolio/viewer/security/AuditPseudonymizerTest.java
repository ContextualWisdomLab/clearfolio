package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuditPseudonymizerTest {

    private static final Object SECURITY_PROVIDERS_LOCK = new Object();
    private static final String AUDIT_KEY_ONE = "0123456789abcdef0123456789abcdef";
    private static final String AUDIT_KEY_TWO = "fedcba9876543210fedcba9876543210";

    @Test
    void producesDeterministicVersionedFingerprintForExactIdentifierBytes() {
        AuditPseudonymizer pseudonymizer = new AuditPseudonymizer(AUDIT_KEY_ONE, "2026-08");

        String first = pseudonymizer.fingerprint("Employee-007@example.com");
        String second = pseudonymizer.fingerprint("Employee-007@example.com");

        assertEquals(first, second);
        assertTrue(first.matches("2026-08:[0-9a-f]{32}"));
        assertFalse(first.contains("Employee-007"));
    }

    @Test
    void separatesKeysVersionsAndDomains() {
        String identifier = "approver-123";
        String baseline = new AuditPseudonymizer(
                AUDIT_KEY_ONE,
                "v1",
                "clearfolio:audit-approver:v1"
        ).fingerprint(identifier);

        assertNotEquals(
                baseline,
                new AuditPseudonymizer(
                        AUDIT_KEY_TWO,
                        "v1",
                        "clearfolio:audit-approver:v1"
                ).fingerprint(identifier)
        );
        assertNotEquals(
                baseline,
                new AuditPseudonymizer(
                        AUDIT_KEY_ONE,
                        "v2",
                        "clearfolio:audit-approver:v1"
                ).fingerprint(identifier)
        );
        assertNotEquals(
                baseline,
                new AuditPseudonymizer(
                        AUDIT_KEY_ONE,
                        "v1",
                        "clearfolio:audit-subject:v1"
                ).fingerprint(identifier)
        );
    }

    @Test
    void distinguishesAbsentEmptyAndUnavailableValues() {
        AuditPseudonymizer configured = new AuditPseudonymizer(AUDIT_KEY_ONE, "v1");
        AuditPseudonymizer unavailableWhitespace = new AuditPseudonymizer(" ", "v1");
        AuditPseudonymizer unavailableNull = new AuditPseudonymizer(null, "v1");

        assertEquals("absent:v1", configured.fingerprint(null));
        assertTrue(configured.fingerprint("").matches("v1:[0-9a-f]{32}"));
        assertNotEquals(configured.fingerprint(null), configured.fingerprint(""));
        assertEquals("unavailable:v1", unavailableWhitespace.fingerprint("approver"));
        assertEquals("unavailable:v1", unavailableNull.fingerprint("approver"));
        assertEquals("absent:v1", unavailableWhitespace.fingerprint(null));
    }

    @Test
    void preservesUnicodeAndControlCharactersOnlyInsideTheHmacInput() {
        String identifier = "승인자\n\u202E@example.com";
        String fingerprint = new AuditPseudonymizer(AUDIT_KEY_ONE, "unicode-v1")
                .fingerprint(identifier);

        assertTrue(fingerprint.matches("unicode-v1:[0-9a-f]{32}"));
        assertFalse(fingerprint.contains("승인자"));
        assertFalse(fingerprint.contains("example.com"));
        assertFalse(fingerprint.contains("\n"));
        assertFalse(fingerprint.contains("\u202E"));
    }

    @Test
    void defaultsOnlyMissingKeyVersionAndRejectsInvalidExplicitValues() {
        assertTrue(new AuditPseudonymizer(AUDIT_KEY_ONE, null).fingerprint("id").startsWith("v1:"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, " ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, " v1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "v1 ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, " v1 ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "bad/version")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "x".repeat(33))
        );
        assertTrue(
                new AuditPseudonymizer(AUDIT_KEY_ONE, "valid._-9")
                        .fingerprint("id")
                        .startsWith("valid._-9:")
        );
    }

    @Test
    void rejectsMissingDomain() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "v1", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer(AUDIT_KEY_ONE, "v1", " ")
        );
    }

    @Test
    void wrapsMissingHmacProviderAsStableInternalFailure() {
        synchronized (SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> removedProviders = hmacProviderPositions();
            for (ProviderPosition providerPosition : removedProviders) {
                Security.removeProvider(providerPosition.provider().getName());
            }
            try {
                AuditPseudonymizer pseudonymizer = new AuditPseudonymizer(AUDIT_KEY_ONE, "v1");
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> pseudonymizer.fingerprint("approver")
                );
                assertEquals("audit pseudonym HMAC unavailable", exception.getMessage());
            } finally {
                removedProviders.stream()
                        .sorted(Comparator.comparingInt(ProviderPosition::position))
                        .forEach(providerPosition -> Security.insertProviderAt(
                                providerPosition.provider(),
                                providerPosition.position()
                        ));
            }
        }
    }

    private static List<ProviderPosition> hmacProviderPositions() {
        Provider[] providers = Security.getProviders();
        List<ProviderPosition> positions = new ArrayList<>();
        for (int index = 0; index < providers.length; index++) {
            Provider provider = providers[index];
            if (provider.getService("Mac", "HmacSHA256") != null) {
                positions.add(new ProviderPosition(provider, index + 1));
            }
        }
        return positions;
    }

    private record ProviderPosition(Provider provider, int position) {
    }
}
