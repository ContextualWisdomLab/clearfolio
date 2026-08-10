package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

class IdentityVerifierContractTest {

    @Test
    void verifierResolvesOpaqueBearerInputIntoTenantContext() {
        TenantContext expected = new TenantContext(
                "tenant-a",
                "subject-a",
                Set.of(TenantPermissions.JOB_READ)
        );
        IdentityVerifier verifier = (profileId, bearerToken) -> {
            assertEquals("enterprise-primary", profileId);
            assertEquals("eyJhbGciOiJSUzI1NiJ9.payload.signature", bearerToken);
            return expected;
        };

        TenantContext actual = verifier.verify(
                "enterprise-primary",
                "eyJhbGciOiJSUzI1NiJ9.payload.signature"
        );

        assertSame(expected, actual);
        assertEquals("identity-verifier-v1", IdentityVerifier.CONTRACT_VERSION);
    }

    @Test
    void rejectedFailureUsesControlledCategoryAndMessage() {
        IdentityVerificationException failure = new IdentityVerificationException(
                IdentityVerificationException.FailureKind.REJECTED
        );

        assertEquals(IdentityVerificationException.FailureKind.REJECTED, failure.failureKind());
        assertEquals("identity verification rejected", failure.getMessage());
    }

    @Test
    void unavailableFailureUsesControlledCategoryAndMessage() {
        IdentityVerificationException failure = new IdentityVerificationException(
                IdentityVerificationException.FailureKind.UNAVAILABLE
        );

        assertEquals(IdentityVerificationException.FailureKind.UNAVAILABLE, failure.failureKind());
        assertEquals("identity verification unavailable", failure.getMessage());
    }

    @Test
    void failureRejectsMissingCategory() {
        assertThrows(NullPointerException.class, () -> new IdentityVerificationException(null));
    }
}
