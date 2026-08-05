# Audit identifier pseudonymization

## Decision

Clearfolio must not write raw approver identifiers or approval tokens to application logs. Policy-override audit events use a domain-separated keyed HMAC for the approver identifier and a non-reversible token fingerprint for the already high-entropy approval signature. Authentication-token handling is outside this policy-override logging contract and remains governed by the repository-wide logging and authorization controls.

The approver field is named `approverFingerprint`, not `approverId`, so downstream log consumers cannot mistake pseudonymous data for the source identifier. Pseudonymized values remain personal data when they can be related back to a person using separately held information; they are not treated as anonymized data.

## Cryptographic contract

### Policy override key

A configured `conversion.policy-override-secret` authorizes blocked-document policy exceptions and must contain at least 32 UTF-8 bytes. Blank or absent configuration keeps policy override disabled. A nonblank value below the minimum fails application startup before any conversion endpoint can accept traffic. The startup gate measures encoded bytes rather than Java character count, does not log the supplied value, and remains independent of the audit-key separation check.

Deployments must generate this key from a cryptographically secure random source and must not use a password, person or tenant identifier, repository token, or other human-memorable value. The minimum-length gate prevents a weak configured secret from reducing the effective security of the HMAC approval token even when the HMAC algorithm itself is correctly implemented (National Institute of Standards and Technology, 2008; Turan & Brandão, 2024).

### Approver identifier

The approver fingerprint is calculated as follows:

```text
HMAC-SHA-256(
  dedicated_audit_key,
  UTF-8("clearfolio:audit-approver:v1\n" + exact_approver_identifier)
)
```

The first 128 bits are encoded as lowercase hexadecimal and prefixed by the non-sensitive key version:

```text
<key-version>:<32 lowercase hexadecimal characters>
```

The implementation preserves the exact Java string bytes supplied after the policy override has passed its existing identity validation. It does not lowercase, Unicode-normalize, or trim inside the pseudonymizer because those transformations would silently alter identity semantics. Null input produces `absent:<key-version>`. An empty Java string is not absent: it is processed as a zero-length identifier through the same domain-separated HMAC and produces a normal versioned fingerprint. A missing dedicated key produces `unavailable:<key-version>` and never falls back to plaintext, the policy-signing secret, or an unkeyed identifier hash.

A configured audit pseudonym secret must contain at least 32 UTF-8 bytes and must be generated from a cryptographically secure random source. The byte-length gate prevents accidentally deploying a short human-memorable secret whose effective strength would bound the HMAC protection. Blank or absent configuration retains the explicit non-correlatable `unavailable` behavior; a nonblank weak key fails application startup. FIPS 198-1 remains the current final NIST HMAC standard while NIST SP 800-224 remains an initial public draft; NIST expects the final SP to be published concurrently with withdrawal of FIPS 198-1 (National Institute of Standards and Technology, 2008, 2025; Turan & Brandão, 2024).

Only an absent key-version property defaults to `v1`. Explicit blank, oversized, or unsafe key-version values fail application startup so one version label can never identify multiple key generations accidentally. The accepted format is one to 32 Java UTF-16 code units matching the implementation-equivalent expression `^[\p{L}\p{Nd}._-]{1,32}$`: each character must satisfy Java `Character.isLetterOrDigit` or be `.`, `_`, or `-`. The value is retained as a Java Unicode string and written by the configured log encoding; deployments use UTF-8 log output. Control characters, separators, whitespace, slashes, and other punctuation are rejected.

### Approval token

The approval token is a policy-override HMAC signature and is therefore already a high-entropy authentication value. The audit-only token fingerprint is calculated independently as follows:

```text
SHA-256(UTF-8(exact_approval_token))
```

The first eight digest bytes are encoded as 16 lowercase hexadecimal characters and written as `tokenFingerprint`. The fingerprint is unkeyed and has no domain prefix because it is used only as a short diagnostic correlation value for an already high-entropy signature; it must never be accepted as an authentication credential or used to validate a policy override. Null, empty, and blank approval tokens are rejected by request validation before fingerprinting, so the audit fingerprint function has no absent or empty sentinel contract.

## Runtime secret loading

Runtime key material is supplied through Spring Boot's config-tree property source rather than direct secret-bearing environment variables. The default mount is `/run/secrets/clearfolio/`; `CLEARFOLIO_SECRET_CONFIG_DIR` may select another bootstrap directory but must not contain a secret value.

The secret store or orchestrator mounts files with these exact names:

```text
conversion.policy-override-secret
conversion.audit-pseudonym-secret
conversion.audit-pseudonym-key-version
```

Spring reads each file's contents as the corresponding property. The deployment must restrict file ownership and mode, prevent inclusion in container images and support bundles, and avoid logging the imported values. If the optional config tree is absent, the application retains safe disabled defaults. Production policy must require the needed values before enabling policy override operations.

## Key ownership and rotation

- `conversion.policy-override-secret` is an authorization key owned by the security function. It must contain at least 32 UTF-8 bytes, be generated from a cryptographically secure random source, and be rotated through the deployment secret manager.
- `conversion.audit-pseudonym-secret` is owned by the security or privacy operations function and must be stored in the deployment secret manager.
- The configured audit value must contain at least 32 UTF-8 bytes and should be a uniformly random 256-bit-or-stronger value rather than a password or identifier.
- The application startup guard rejects identical nonblank values for `conversion.audit-pseudonym-secret` and `conversion.policy-override-secret`. Deployment policy must additionally keep the audit key operationally separate from tenant-claims signing keys, encryption keys, and API credentials; those keys are owned by their respective subsystems and are not all available to this component's startup guard.
- `conversion.audit-pseudonym-key-version` is a non-secret identifier such as `2026-08` but is mounted with the same versioned configuration bundle to keep key and label rotation atomic.
- Rotation changes both the secret and version. During an investigation that spans a rotation boundary, operators must treat fingerprints from different versions as intentionally unlinkable unless an approved, separately controlled re-identification process exists.
- Retired keys must not remain in application configuration. Any escrow or incident-response copy must be access-controlled, time-bounded, and audited.

## Retention and access

Audit log retention must be limited to the shortest period required by the documented security, contractual, and regulatory purpose. Read access is restricted by least privilege. Export, search, re-identification, and deletion workflows must be auditable. Logs and pseudonym keys must never be stored in the same access domain.

## Incident response

If the audit pseudonym key is suspected to be exposed:

1. Rotate the key and version immediately.
2. Preserve affected log ranges under incident hold without broadening access.
3. Determine whether dictionary attacks against likely identifiers were feasible.
4. Treat exposed pseudonymized records as potentially exposed personal data.
5. Follow the applicable breach-assessment and notification process.
6. Verify that no raw identifiers, approval tokens, or key material were written to logs.

## Verification requirements

Automated tests must prove:

- determinism within one key version and domain;
- separation across keys, versions, and domains;
- startup rejection of configured policy-override and audit keys shorter than 32 UTF-8 bytes;
- acceptance of multibyte policy keys based on encoded byte length rather than character count;
- rejection of invalid explicit key versions;
- startup rejection when policy and audit purposes reuse the same nonblank key;
- distinct absent, empty, and unavailable approver behavior;
- rejection of null, empty, or blank approval tokens before token fingerprinting;
- safe handling of Unicode and control characters;
- no raw approver identifier or approval token in captured policy-override audit output;
- stable failure behavior if the HMAC provider is unavailable;
- 100% JaCoCo line and branch coverage for the `com.clearfolio.viewer.*` production package.

## References

European Parliament and Council of the European Union. (2016). *Regulation (EU) 2016/679 of the European Parliament and of the Council of 27 April 2016 on the protection of natural persons with regard to the processing of personal data and on the free movement of such data (General Data Protection Regulation)*. *Official Journal of the European Union, L 119*, 1–88.

National Institute of Standards and Technology. (2008). *The keyed-hash message authentication code (HMAC)* (FIPS PUB 198-1). U.S. Department of Commerce. https://doi.org/10.6028/NIST.FIPS.198-1

National Institute of Standards and Technology. (2025, June 23). *Proposed withdrawal of FIPS 198-1, HMAC*. Computer Security Resource Center. https://csrc.nist.gov/News/2025/proposed-withdrawal-of-fips-198-1-hmac

OWASP Foundation. (n.d.). *Logging cheat sheet*. OWASP Cheat Sheet Series. Retrieved August 4, 2026, from https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

Turan, M. S., & Brandão, L. T. A. N. (2024). *Keyed-hash message authentication code (HMAC): Specification of HMAC and recommendations for message authentication* (NIST SP 800-224 Initial Public Draft). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-224.ipd
