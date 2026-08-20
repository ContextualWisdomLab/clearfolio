package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdentityKeySnapshotTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-08-11T04:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-11T04:05:00Z");

    @Test
    void preservesImmutableGenerationBoundKeyAuthority() {
        Set<String> mutableKeyIds = new HashSet<>(Set.of("key-a", "key-b"));
        IdentityKeySnapshot snapshot = new IdentityKeySnapshot(
                "workforce",
                3,
                FETCHED_AT,
                EXPIRES_AT,
                mutableKeyIds
        );
        mutableKeyIds.clear();

        assertEquals("workforce", snapshot.verifierProfileId());
        assertEquals(3, snapshot.generation());
        assertEquals(FETCHED_AT, snapshot.fetchedAt());
        assertEquals(EXPIRES_AT, snapshot.expiresAt());
        assertEquals(Set.of("key-a", "key-b"), snapshot.trustedKeyIds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.trustedKeyIds().add("key-c"));

        assertTrue(snapshot.isCurrentAt(FETCHED_AT));
        assertTrue(snapshot.isCurrentAt(EXPIRES_AT.minusNanos(1)));
        assertFalse(snapshot.isCurrentAt(FETCHED_AT.minusNanos(1)));
        assertFalse(snapshot.isCurrentAt(EXPIRES_AT));
        assertTrue(snapshot.authorizes("key-a", 3, FETCHED_AT));
        assertFalse(snapshot.authorizes("key-c", 3, FETCHED_AT));
        assertFalse(snapshot.authorizes("key-a", 2, FETCHED_AT));
        assertFalse(snapshot.authorizes("key-a", 3, EXPIRES_AT));
    }

    @Test
    void rejectsMalformedSnapshotAuthorityAndVerificationInputs() {
        assertEquals("verifierProfileId", assertThrows(
                NullPointerException.class,
                () -> new IdentityKeySnapshot(null, 1, FETCHED_AT, EXPIRES_AT, Set.of("key-a"))
        ).getMessage());
        assertEquals("verifierProfileId must not be blank", assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityKeySnapshot("   ", 1, FETCHED_AT, EXPIRES_AT, Set.of("key-a"))
        ).getMessage());
        assertEquals("generation must be positive", assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityKeySnapshot("workforce", 0, FETCHED_AT, EXPIRES_AT, Set.of("key-a"))
        ).getMessage());
        assertEquals("fetchedAt", assertThrows(
                NullPointerException.class,
                () -> new IdentityKeySnapshot("workforce", 1, null, EXPIRES_AT, Set.of("key-a"))
        ).getMessage());
        assertEquals("expiresAt", assertThrows(
                NullPointerException.class,
                () -> new IdentityKeySnapshot("workforce", 1, FETCHED_AT, null, Set.of("key-a"))
        ).getMessage());
        assertEquals("fetchedAt must precede expiresAt", assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityKeySnapshot("workforce", 1, EXPIRES_AT, EXPIRES_AT, Set.of("key-a"))
        ).getMessage());
        assertEquals("trustedKeyIds", assertThrows(
                NullPointerException.class,
                () -> new IdentityKeySnapshot("workforce", 1, FETCHED_AT, EXPIRES_AT, null)
        ).getMessage());
        assertEquals("trustedKeyIds must not be empty", assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityKeySnapshot("workforce", 1, FETCHED_AT, EXPIRES_AT, Set.of())
        ).getMessage());

        Set<String> nullKeyIds = new HashSet<>();
        nullKeyIds.add(null);
        assertEquals("trustedKeyIds must not contain null", assertThrows(
                NullPointerException.class,
                () -> new IdentityKeySnapshot("workforce", 1, FETCHED_AT, EXPIRES_AT, nullKeyIds)
        ).getMessage());
        assertEquals("trustedKeyIds must not contain blank identifiers", assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityKeySnapshot("workforce", 1, FETCHED_AT, EXPIRES_AT, Set.of(" "))
        ).getMessage());

        IdentityKeySnapshot snapshot = new IdentityKeySnapshot(
                "workforce",
                1,
                FETCHED_AT,
                EXPIRES_AT,
                Set.of("key-a")
        );
        assertEquals("now", assertThrows(
                NullPointerException.class,
                () -> snapshot.isCurrentAt(null)
        ).getMessage());
        assertEquals("keyId", assertThrows(
                NullPointerException.class,
                () -> snapshot.authorizes(null, 1, FETCHED_AT)
        ).getMessage());
        assertFalse(snapshot.authorizes(" ", 1, FETCHED_AT));
        assertFalse(snapshot.authorizes("key-a", 0, FETCHED_AT));
    }
}
