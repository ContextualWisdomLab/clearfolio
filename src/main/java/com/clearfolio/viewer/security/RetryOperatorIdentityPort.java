package com.clearfolio.viewer.security;

/**
 * Produces privacy-safe audit identities for operators who retry conversion jobs.
 *
 * <p>This port keeps HTTP controllers and conversion-domain services independent
 * from cryptographic implementation details. Implementations must never return
 * the raw subject identifier or an unkeyed digest of a low-entropy identifier.
 * The returned value is audit correlation metadata only; it is not an
 * authentication principal, authorization decision, or tenant authority.</p>
 */
@FunctionalInterface
public interface RetryOperatorIdentityPort {

    /**
     * Converts an authenticated subject identifier into retry-audit metadata.
     *
     * @param subjectId exact authenticated subject identifier supplied by the
     *                  already-verified tenant context
     * @return privacy-safe, versioned audit identity that does not expose the
     *         subject identifier or a dictionary-recoverable unkeyed digest
     * @throws IllegalStateException when the configured cryptographic primitive
     *         cannot be used safely
     */
    String pseudonymize(String subjectId);
}
