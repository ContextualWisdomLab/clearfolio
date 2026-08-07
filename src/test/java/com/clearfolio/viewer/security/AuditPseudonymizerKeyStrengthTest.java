package com.clearfolio.viewer.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the minimum configured key-strength contract for audit pseudonyms.
 */
class AuditPseudonymizerKeyStrengthTest {

    @Test
    void rejectsConfiguredSecretShorterThanThirtyTwoUtf8Bytes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AuditPseudonymizer("0123456789abcdef0123456789abcde", "v1")
        );

        assertTrue(exception.getMessage().contains("at least 32 UTF-8 bytes"));
    }

    @Test
    void acceptsConfiguredSecretWithThirtyTwoUtf8Bytes() {
        AuditPseudonymizer pseudonymizer = assertDoesNotThrow(
                () -> new AuditPseudonymizer("0123456789abcdef0123456789abcdef", "v1")
        );

        assertTrue(pseudonymizer.fingerprint("approver").startsWith("v1:"));
    }

    @Test
    void measuresConfiguredSecretLengthAsUtf8Bytes() {
        AuditPseudonymizer pseudonymizer = assertDoesNotThrow(
                () -> new AuditPseudonymizer("가나다라마바사아자차카타파하가나", "v1")
        );

        assertTrue(pseudonymizer.fingerprint("approver").startsWith("v1:"));
    }
}
