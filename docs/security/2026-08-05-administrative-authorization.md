# Tenant-scoped administrative authorization

## Decision

Clearfolio's administrative job endpoints are not global superuser APIs. They are tenant-scoped operations that evaluate signed subject claims, an explicit administrative permission, the requested operation, and the target job's tenant ownership on every request.

The implementation follows deny-by-default and least-privilege principles. Listing requires `admin:read`; deletion and dead-letter retry require `admin:write`. Possessing an opaque UUID is never sufficient authorization. Missing and cross-tenant jobs intentionally return the same not-found response so an object identifier cannot be used to enumerate another tenant's documents or operational state.

Administrative endpoints never fall back to unsigned demo-header mode. If the tenant-claims HMAC verifier is absent or its configured key contains fewer than 32 UTF-8 bytes, the endpoints return `503 Service Unavailable` before repository access. This makes a missing or weak trust anchor an observable deployment failure rather than an authorization bypass.

## Trust boundary

The `X-Clearfolio-*` claim headers are an internal adapter contract between Clearfolio and an authenticated gateway or host such as naruon. They are not public client credentials. The upstream gateway must authenticate the caller, construct canonical tenant, subject, permission, and issue-time claims, and sign them with the tenant-claims HMAC key.

Clearfolio verifies the signature and freshness before evaluating permissions. Deployments must strip untrusted inbound copies of these headers before adding verified claims. The service remains standalone because the claim verifier is an injectable component, but production must not expose the internal header adapter directly to arbitrary clients.

The tenant-claims and artifact-token HMAC secrets are read from the shared Spring config-tree secret mount as `clearfolio.tenant-claims.hmac-secret` and `clearfolio.artifact-token.secret`. The buyer-demo profile no longer maps either secret-bearing environment variable directly into runtime configuration. Environment variables may select non-secret operational values or bootstrap a mounted credential store, but runtime authentication and artifact-link signing read the mounted properties. Each mounted signing key must be provisioned independently through the deployment platform's secret manager; the tenant-claims key must contain at least 32 UTF-8 bytes for privileged administrative endpoints.

## Authorization sequence

Every endpoint applies the same fail-closed sequence:

1. Confirm that a strong signed-claim verifier is configured; otherwise return `503` before service access.
2. Parse the tenant, subject, permissions, issue time, and claim signature.
3. Verify signed claims and their freshness.
4. Require the action-specific permission.
5. Pass the verified `TenantContext` into the object-specific service mutation.
6. Select and mutate the target through one tenant-scoped persistence operation.
7. Return a non-enumerating not-found response for absent or cross-tenant objects.
8. Emit privacy-safe authorization evidence for the resulting outcome.

List responses apply the tenant predicate before job objects cross the repository boundary and then apply the optional dead-letter status filter. Delete and retry do not perform controller-level or service-level read-then-write authorization. Their service contracts pass the authenticated tenant to atomic repository or state-store operations, so non-HTTP callers cannot reach an unscoped administrative mutation by bypassing the controller.

Deletion first performs the tenant-predicate repository deletion. Artifact removal is attempted only after that owned deletion succeeds, so a missing or cross-tenant identifier cannot delete another tenant's artifact. The current removal is best effort: an artifact-store exception is logged and does not restore the already deleted job. This slice does not persist a deletion receipt, enqueue cleanup work, retry a failed removal, or expose aggregate cleanup evidence. A failure can therefore leave orphaned artifact bytes. Issue #263 owns the restart-safe deletion-receipt, transactional-outbox, idempotent cleanup-worker, privacy-safe evidence, bounded retry, and deterministic recovery boundary required for production deployment.

Retry receives one atomic state-store outcome: `ACCEPTED`, concealed `NOT_FOUND`, or `NOT_ELIGIBLE`. The worker is enqueued only after the state store has atomically verified ownership and moved the owned dead-lettered job back to submitted state.

A missing job identifier is treated as an absent object at every tenant-scoped lookup, delete, and retry boundary. Repository adapters return an empty lookup, `false`, or `NOT_FOUND` without invoking a global lookup, mutating stored state, deleting an artifact, or enqueueing work. This keeps internal and modular callers on the same non-enumerating contract even when a malformed or incomplete adapter call bypasses HTTP path binding.

The historical unscoped delete method and two-argument retry method remain compatibility contracts for non-administrative adapters only. Their tenant-aware service, repository, and state-store defaults fail closed without reading a job or invoking either legacy mutation. A production adapter must explicitly override the tenant-aware methods and perform tenant selection and mutation within one persistence boundary before an administrative request can succeed. Clearfolio's in-memory reference implementation supplies those scoped atomic overrides.

The content-hash secondary index is tenant-bound, and each conversion-job UUID is an immutable lifecycle identity. The in-memory adapter reserves a UUID when it first accepts a job. Saving the exact same live object may be idempotent, but any distinct live object or any later object using a deleted UUID is rejected before secondary-index work. Deletion removes the live record and its tenant-and-content-hash index while retaining the UUID reservation. Lookup also validates that the current UUID record matches the requested tenant-and-hash key. These rules prevent a queued UUID, stale index, or stale preliminary observation from being rebound to another tenant's job.

The in-memory adapter updates the primary UUID map, the permanent identifier reservation, and the tenant-content secondary index under one shared critical section for save, find-or-store, indexed lookup, tenant-scoped deletion, and compatibility deletion. A concurrent same-UUID save therefore waits for the active operation and then fails closed; deletion never releases the identifier for reuse. Durable adapters must provide the equivalent invariant with a database uniqueness constraint plus a durable tombstone or lifecycle-generation reservation in the same transaction. Process-local locking is a reference-adapter mechanism, not an interoperability contract.

This identifier rule closes the retry-to-worker handoff race even though the current in-memory worker queue carries a UUID: after an authorized tenant retry transitions the original object, another tenant cannot replace that UUID before enqueue or worker lookup. The durable lifecycle work tracked in issue #263 will strengthen this boundary further with tenant-and-generation deletion receipts and outbox records, but it must not be used to justify a tenant-crossing retry path in the current slice.

## Audit evidence

Administrative evidence contains only:

- a controlled action code;
- a controlled outcome code;
- HTTP status;
- tenant, actor, and conversion-job HMAC fingerprints in three separate domains;
- a numeric result count for list operations.

It does not contain raw tenant identifiers, raw subject identifiers, raw job UUIDs, claim signatures, permission headers, filenames, job messages, document text, or artifact bytes. Job correlation uses a dedicated keyed HMAC domain so resource references cannot be joined directly with API paths, databases, support exports, or external telemetry. The retry provenance stored with a job uses the actor-domain fingerprint rather than the source subject identifier. Pseudonymized values remain personal data and inherit the retention, access, rotation, and incident-response requirements in `2026-08-04-audit-pseudonymization.md`.

No durable artifact-cleanup evidence exists in this slice. Operators must not interpret an application log entry as a persisted deletion receipt or retry record. Buyer-facing and production-readiness materials must keep issue #263 open until a durable cleanup adapter and recovery evidence are integrated.

## Verification requirements

Automated tests must exercise the real signed-claim verifier and prove:

- absent and weak verifier keys make privileged endpoints unavailable before service access;
- missing, malformed, expired, and incorrectly signed claims fail before service access;
- missing `admin:read` or `admin:write` permissions fail before service access;
- list results contain only tenant-owned jobs for all dead-letter filter states;
- missing and cross-tenant delete/retry targets produce indistinguishable not-found responses;
- a missing job identifier fails closed across tenant-scoped lookup, delete, and retry while leaving existing stored state unchanged;
- delete and retry cross tenant-aware persistence boundaries without a separate lookup or unscoped mutation call;
- failed tenant-scoped deletion never touches the artifact store, while successful owned deletion attempts artifact removal only after repository authorization;
- an artifact-removal failure does not roll back the authorized job deletion and is not represented as durable cleanup evidence in this slice;
- compatibility-only service, repository, and state-store adapters cannot reach global lookup, delete, or retry methods through tenant-aware defaults;
- saving the exact same live object is idempotent, while a distinct object with a live UUID is rejected without changing tenant, hash-index, or lifecycle ownership;
- deletion removes the live job and secondary index but leaves the UUID reserved, so both direct save and atomic find-or-store reject later reuse;
- a colliding find-or-store candidate fails before index ownership changes;
- a concurrent tenant-scoped delete cannot release or transfer UUID ownership, and a waiting same-UUID save fails before replacement index work;
- an accepted tenant retry cannot be followed by a same-UUID cross-tenant replacement before enqueue or worker lookup;
- stale observations cannot delete or retry a job owned by another tenant;
- the durable retry state store rejects invalid, missing, cross-tenant, and ineligible targets without an unauthorized transition or worker enqueue;
- accepted retry provenance is a domain-separated keyed fingerprint, never a raw or unkeyed subject value;
- not-found, not-eligible, repository failure, artifact-removal failure, and retry failure paths return stable non-leaking responses;
- both artifact-token and tenant-claims signing keys are sourced from the shared config-tree mount rather than secret-valued profile environment placeholders;
- audit output contains no raw tenant, subject, job UUID, signature, filename, or document data;
- JaCoCo reports 100% line and branch coverage for the `com.clearfolio.viewer.*` production package.

## References

Hu, V. C., Ferraiolo, D., Kuhn, D. R., Schnitzer, A., Sandlin, K., Miller, R., & Scarfone, K. (2014). *Guide to attribute based access control (ABAC) definition and considerations* (NIST Special Publication 800-162, updated August 2, 2019). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-162

OWASP Foundation. (2023). *API1:2023 broken object level authorization*. OWASP API Security Top 10. https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/

OWASP Foundation. (n.d.). *Authorization cheat sheet*. OWASP Cheat Sheet Series. Retrieved August 5, 2026, from https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
