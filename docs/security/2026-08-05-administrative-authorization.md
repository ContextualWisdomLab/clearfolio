# Tenant-scoped administrative authorization

## Decision

Clearfolio's administrative job endpoints are not global superuser APIs. They are tenant-scoped operations that evaluate signed subject claims, an explicit administrative permission, the requested operation, and the target job's tenant ownership on every request.

The implementation follows deny-by-default and least-privilege principles. Listing requires `admin:read`; deletion and dead-letter retry require `admin:write`. Possessing an opaque UUID is never sufficient authorization. Missing and cross-tenant jobs intentionally return the same not-found response so an object identifier cannot be used to enumerate another tenant's documents or operational state.

Administrative endpoints never fall back to unsigned demo-header mode. If the tenant-claims HMAC verifier is absent or its configured key contains fewer than 32 UTF-8 bytes, the endpoints return `503 Service Unavailable` before repository access. This makes a missing or weak trust anchor an observable deployment failure rather than an authorization bypass.

## Trust boundary

The `X-Clearfolio-*` claim headers are an internal adapter contract between Clearfolio and an authenticated gateway or host such as naruon. They are not public client credentials. The upstream gateway must authenticate the caller, construct canonical tenant, subject, permission, and issue-time claims, and sign them with the tenant-claims HMAC key.

Clearfolio verifies the signature and freshness before evaluating permissions. Deployments must strip untrusted inbound copies of these headers before adding verified claims. The service remains standalone because the claim verifier is an injectable component, but production must not expose the internal header adapter directly to arbitrary clients.

The tenant-claims HMAC secret is read from the shared Spring config-tree secret mount as `clearfolio.tenant-claims.hmac-secret`. The buyer-demo profile no longer maps a secret-bearing environment variable directly into runtime configuration. Environment variables may select non-secret operational values or bootstrap a mounted credential store, but runtime authentication reads the mounted property. The mounted tenant-claims key must contain at least 32 UTF-8 bytes for privileged administrative endpoints.

## Authorization sequence

Every endpoint applies the same fail-closed sequence:

1. Confirm that a strong signed-claim verifier is configured; otherwise return `503` before service access.
2. Parse the tenant, subject, permissions, issue time, and claim signature.
3. Verify signed claims and their freshness.
4. Require the action-specific permission.
5. Pass the verified `TenantContext` into the object-specific service mutation.
6. Select the target through a tenant-scoped repository lookup before evaluating or applying the mutation.
7. Return a non-enumerating not-found response for absent or cross-tenant objects.
8. Emit privacy-safe authorization evidence for the resulting outcome.

List responses filter the complete repository result to the verified tenant before applying the optional dead-letter status filter. Delete and retry do not perform controller-level read-then-write authorization. Their service contracts receive the verified tenant context and enforce ownership at the mutation boundary, so non-HTTP callers cannot reach an unscoped administrative mutation by bypassing the controller.

The legacy two-argument retry method remains only as a compatibility contract for non-administrative adapters. Administrative HTTP flows call the tenant-aware three-argument method. The durable implementation performs the tenant-scoped selection before invoking the shared state transition and therefore does not rely on a controller-side ownership check.

## Audit evidence

Administrative evidence contains only:

- a controlled action code;
- a controlled outcome code;
- HTTP status;
- tenant and actor HMAC fingerprints in separate domains;
- an opaque job UUID when applicable;
- a numeric result count for list operations.

It does not contain raw tenant identifiers, raw subject identifiers, claim signatures, permission headers, filenames, job messages, document text, or artifact bytes. The retry provenance stored with a job uses the actor-domain fingerprint rather than the source subject identifier. Pseudonymized values remain personal data and inherit the retention, access, rotation, and incident-response requirements in `2026-08-04-audit-pseudonymization.md`.

## Verification requirements

Automated tests must exercise the real signed-claim verifier and prove:

- absent and weak verifier keys make privileged endpoints unavailable before service access;
- missing, malformed, expired, and incorrectly signed claims fail before service access;
- missing `admin:read` or `admin:write` permissions fail before service access;
- list results contain only tenant-owned jobs for all dead-letter filter states;
- missing and cross-tenant delete/retry targets produce indistinguishable not-found responses;
- delete and retry cross a tenant-aware service boundary without a separate controller lookup or unscoped administrative mutation call;
- the durable retry service rejects null, missing, and cross-tenant contexts without state transition or worker enqueue;
- accepted retry provenance is a domain-separated keyed fingerprint, never a raw or unkeyed subject value;
- not-found, not-eligible, repository failure, deletion failure, and retry failure paths return stable non-leaking responses;
- audit output contains no raw tenant, subject, signature, filename, or document data;
- JaCoCo reports 100% line and branch coverage for the `com.clearfolio.viewer.*` production package.

## References

Hu, V. C., Ferraiolo, D., Kuhn, D. R., Schnitzer, A., Sandlin, K., Miller, R., & Scarfone, K. (2014). *Guide to attribute based access control (ABAC) definition and considerations* (NIST Special Publication 800-162, updated August 2, 2019). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-162

OWASP Foundation. (2023). *API1:2023 broken object level authorization*. OWASP API Security Top 10. https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/

OWASP Foundation. (n.d.). *Authorization cheat sheet*. OWASP Cheat Sheet Series. Retrieved August 5, 2026, from https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
