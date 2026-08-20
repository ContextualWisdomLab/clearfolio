package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class FederatedTokenTimeWindowTest {

    @Test
    void appliesNotBeforeExpirationAndExplicitClockSkew() {
        Instant notBefore = Instant.parse("2026-08-11T03:00:00Z");
        Instant expiresAt = Instant.parse("2026-08-11T04:00:00Z");
        FederatedTokenTimeWindow window = new FederatedTokenTimeWindow(notBefore, expiresAt);
        Duration skew = Duration.ofSeconds(30);

        assertEquals(notBefore, window.notBefore());
        assertEquals(expiresAt, window.expiresAt());
        assertFalse(window.isActiveAt(Instant.parse("2026-08-11T02:59:29Z"), skew));
        assertTrue(window.isActiveAt(Instant.parse("2026-08-11T02:59:30Z"), skew));
        assertTrue(window.isActiveAt(notBefore, Duration.ZERO));
        assertTrue(window.isActiveAt(Instant.parse("2026-08-11T04:00:29Z"), skew));
        assertFalse(window.isActiveAt(Instant.parse("2026-08-11T04:00:30Z"), skew));
        assertFalse(window.isActiveAt(expiresAt, Duration.ZERO));
    }

    @Test
    void allowsAnAbsentNotBeforeClaimWithoutInventingALowerBound() {
        FederatedTokenTimeWindow window = new FederatedTokenTimeWindow(
                null,
                Instant.parse("2026-08-11T04:00:00Z")
        );

        assertEquals(null, window.notBefore());
        assertTrue(window.isActiveAt(Instant.parse("2020-01-01T00:00:00Z"), Duration.ZERO));
    }

    @Test
    void rejectsMalformedWindowAndValidationInputs() {
        Instant expiry = Instant.parse("2026-08-11T04:00:00Z");

        NullPointerException missingExpiry = assertThrows(
                NullPointerException.class,
                () -> new FederatedTokenTimeWindow(null, null)
        );
        assertEquals("expiresAt", missingExpiry.getMessage());

        IllegalArgumentException equalBounds = assertThrows(
                IllegalArgumentException.class,
                () -> new FederatedTokenTimeWindow(expiry, expiry)
        );
        assertEquals("notBefore must precede expiresAt", equalBounds.getMessage());

        IllegalArgumentException reversedBounds = assertThrows(
                IllegalArgumentException.class,
                () -> new FederatedTokenTimeWindow(expiry.plusSeconds(1), expiry)
        );
        assertEquals("notBefore must precede expiresAt", reversedBounds.getMessage());

        FederatedTokenTimeWindow window = new FederatedTokenTimeWindow(null, expiry);
        NullPointerException missingNow = assertThrows(
                NullPointerException.class,
                () -> window.isActiveAt(null, Duration.ZERO)
        );
        assertEquals("now", missingNow.getMessage());

        NullPointerException missingSkew = assertThrows(
                NullPointerException.class,
                () -> window.isActiveAt(expiry.minusSeconds(1), null)
        );
        assertEquals("allowedClockSkew", missingSkew.getMessage());

        IllegalArgumentException negativeSkew = assertThrows(
                IllegalArgumentException.class,
                () -> window.isActiveAt(expiry.minusSeconds(1), Duration.ofSeconds(-1))
        );
        assertEquals("allowedClockSkew must not be negative", negativeSkew.getMessage());
    }
}
