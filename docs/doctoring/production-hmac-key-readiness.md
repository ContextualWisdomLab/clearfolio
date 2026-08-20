# Production HMAC-SHA-256 key readiness

## Decision

Clearfolio production startup requires each effective tenant-claims and artifact-token HMAC-SHA-256 signing key to contain at least **32 UTF-8 bytes**. The two purposes must use byte-distinct keys.

The readiness check evaluates the same effective tenant-claims key that the verifier consumes after removing NUL characters and stripping surrounding Unicode whitespace. Production rejects a configured value that would change under that normalization, so operators cannot mistake the literal configured bytes for a different runtime key.

## Standards basis

RFC 7518 section 3.2 specifies that an HMAC key used with SHA-256 must be at least the size of the hash output: 256 bits. Because Clearfolio uses `HmacSHA256` for tenant claims and artifact-token signatures, a 16-byte minimum is insufficient for the protocol contract even though it may provide a nominal 128-bit input length.

NIST SP 800-57 Part 1 Revision 5 provides the general key-management principle that a key should ordinarily be used for one purpose. Clearfolio therefore rejects purpose reuse between tenant-claim and artifact-token signing. Distinct configured values do not by themselves prove independent generation, entropy, KMS custody, rotation, or compromise recovery; those remain part of issue #319's credential-registry architecture.

NIST SP 800-224 remains an Initial Public Draft at this decision point and is not treated as the sole production authority. FIPS 198-1 remains relevant historical HMAC guidance while NIST progresses its announced migration of HMAC specifications.

## Executable acceptance

The exact-head regression requires:

- former 16-byte tenant and artifact secrets to fail closed;
- effective UTF-8 byte length, rather than character count, to determine acceptance;
- exact 32-byte multibyte and ASCII keys to be accepted;
- null, blank, undersized, NUL-modified, or surrounding-whitespace-modified tenant authority to fail;
- missing, blank, or undersized artifact-token authority to fail;
- byte-identical purpose reuse to fail;
- development fallback behavior to remain outside the production readiness contract.

## Scope boundary

This decision is a startup-strength and purpose-separation guard. It does not replace direct configuration injection with a provider-neutral credential registry, implement active/previous rotation windows, prove key entropy, or establish KMS-backed custody. Those controls remain owned by issue #319 and the ordered credential-registry PR stack.

## References

Barker, E. (2020). *Recommendation for key management: Part 1—General* (NIST Special Publication 800-57 Part 1 Revision 5). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-57pt1r5

Jones, M. (2015). *JSON Web Algorithms (JWA)* (RFC 7518). RFC Editor. https://doi.org/10.17487/RFC7518

National Institute of Standards and Technology. (2008). *The Keyed-Hash Message Authentication Code (HMAC)* (FIPS PUB 198-1). https://doi.org/10.6028/NIST.FIPS.198-1
