# Tenant-scoped administrative authorization

## Decision

Clearfolio's administrative conversion-job endpoints are tenant-scoped control-plane APIs, not global superuser APIs. Every request evaluates signed subject claims, an explicit administrative permission, the requested operation, and the target job's tenant ownership. Listing requires `admin:read`; deletion and dead-letter retry require `admin:write`. Possessing an opaque UUID is never authorization. Missing and cross-tenant jobs intentionally produce the same not-found outcome so an identifier cannot enumerate another tenant's documents or operational state.

The implementation follows deny by default and least privilege. Administrative endpoints never fall back to unsigned demo headers. If the tenant-claims HMAC verifier is absent or its key contains fewer than 32 UTF-8 bytes, the endpoints return `503 Service Unavailable` before repository access. A missing or weak trust anchor is therefore an observable deployment failure rather than an authorization bypass.

## Trust boundary

The `X-Clearfolio-*` headers are an internal adapter contract between Clearfolio and an authenticated gateway or host such as naruon. They are not public client credentials. The upstream gateway must authenticate the caller, remove untrusted inbound copies, construct canonical tenant, subject, permission, and issue-time claims, sign them with the tenant-claims HMAC key, and forward only the verified replacement set.

Clearfolio verifies signature and freshness before permission evaluation. The service remains independently deployable because the verifier is injectable, but production must not expose this internal header adapter directly to arbitrary clients.

Tenant-claims and artifact-token HMAC secrets are read from the shared Spring config-tree mount as `clearfolio.tenant-claims.hmac-secret` and `clearfolio.artifact-token.secret`. The buyer-demo profile does not map either secret-bearing environment variable into runtime configuration. Environment variables may select non-secret operational values or bootstrap a mounted credential source; runtime authentication and artifact-link signing read mounted properties. Each signing key must be provisioned independently through the deployment platform's secret manager.

## Authorization sequence

Every administrative endpoint applies the same fail-closed sequence:

1. Confirm that a strong signed-claim verifier is configured; otherwise return `503` before service access.
2. Parse tenant, subject, permissions, issue time, and signature.
3. Verify canonical claims, signature, and freshness.
4. Require the action-specific permission.
5. Pass the verified `TenantContext` into the object-specific service operation.
6. Select and mutate through one tenant-scoped persistence boundary.
7. Conceal absent and cross-tenant objects with the same not-found result.
8. Emit privacy-safe authorization and lifecycle evidence.

List responses apply the tenant predicate before job objects cross the repository boundary and then apply the optional dead-letter filter. Delete and retry do not perform controller-level read-then-write authorization. Their service contracts pass the authenticated tenant to atomic repository or state-store operations, so a non-HTTP caller cannot bypass the controller and reach an unscoped administrative mutation.

## Immutable job and retry handoff boundary

A missing job identifier is treated as an absent object at every tenant-scoped lookup, delete, and retry boundary. Repository adapters return an empty lookup, `false`, or `NOT_FOUND` without invoking a global lookup, changing stored state, deleting an artifact, or enqueueing work.

Compatibility-only unscoped delete and two-argument retry methods remain available for non-administrative adapters. Their tenant-aware service, repository, and state-store defaults fail closed without reading a job or invoking legacy mutation. A production adapter must explicitly implement tenant selection and mutation in one persistence boundary. The in-memory reference adapter supplies those scoped atomic overrides.

The content-hash secondary index is tenant-bound, and each conversion-job UUID is an immutable lifecycle identity. The in-memory adapter permanently reserves a UUID when first accepted. Saving the exact same live object may be idempotent; a distinct live object or any later object using a tombstoned UUID is rejected before secondary-index work. Deletion removes the live record and tenant-plus-content-hash index while retaining the reservation. Indexed lookup also verifies that the current UUID record matches the requested tenant and hash.

Primary UUID state, permanent reservation, and the tenant-content index are updated under one shared critical section. A concurrent same-UUID save waits for the active mutation and then fails closed; deletion never releases the identifier for reuse. Durable adapters must provide the equivalent invariant with a database uniqueness constraint and durable tombstone or lifecycle-generation reservation in the same transaction.

This invariant closes the retry-to-worker handoff race in the reference adapter even though the worker queue carries a UUID. After an authorized tenant retry transitions the original object, another tenant cannot replace that UUID before enqueue or worker lookup. Distributed adapters must additionally bind queued work to tenant and immutable generation, then revalidate both at execution time.

## Durable artifact-deletion boundary

Authorized deletion is routed through `DurableDocumentDeletionService` and `ArtifactDeletionCoordinator`. The reference runtime now implements a durable deletion receipt and bounded cleanup lifecycle rather than best-effort-only removal.

The lifecycle is receipt first:

1. Under the per-job lifecycle lock, persist `DELETION_REQUESTED` with a controlled non-digest `pending` checksum marker before the first artifact-store read.
2. If the first read fails, preserve metadata, retain the durable receipt, record only a controlled low-cardinality failure, and allow startup or scheduled recovery to resume.
3. After a successful read, bind the same receipt identity exactly once to the artifact SHA-256 or the explicit confirmed-absence digest. Replay rejects reverse or conflicting binding.
4. Only after exact checksum binding may the tenant-scoped metadata tombstone be applied.
5. Mark cleanup pending, compare the exact generation, delete the artifact idempotently, and record completion or a controlled retryable failure.
6. Replay incomplete receipts at application startup and on a bounded fixed-delay schedule.

Within one running process, bounded recovery rotates its starting position through the deterministic pending-receipt order. A permanently failing oldest receipt therefore remains durable and retryable without starving newer work behind a smaller batch limit. The fairness cursor is process-local coordination state, not receipt evidence: after restart the coordinator safely begins again from deterministic request order. A high-churn multi-instance adapter that requires fairness across process loss must persist an equivalent lease, cursor, or next-attempt ordering without weakening receipt identity, generation fencing, or idempotency.

`LifecycleFencedArtifactStore` shares the per-job lifecycle lock and rejects publication after a deletion receipt exists. An in-flight publication that completes before receipt acceptance becomes the captured generation and is deleted; a later publication fails closed. Aggregate metrics expose only completed, failed, pending, and bounded execution evidence. They do not use tenant, job, digest, path, filename, exception message, or other high-cardinality dimensions.

The append-only file adapter forces each accepted receipt snapshot before reporting it durable. It supports restart replay and preserves incomplete cleanup evidence. The in-memory adapter remains available for standalone tests and ephemeral use.

This reference boundary is intentionally narrower than a distributed transaction. It has no cross-resource transactional outbox spanning an external database and object store, no distributed generation fence shared by multiple service instances, and no remote-object-store atomicity guarantee. A multi-instance production adapter must add durable uniqueness, object-version preconditions or compare-and-set semantics, an idempotent outbox/consumer boundary, bounded retry and dead-letter operations, and operator recovery evidence. Issue #263 remains the umbrella for those distributed adapters and later truthful API/UI deletion-state semantics.

## Audit evidence

Administrative evidence contains only:

- a controlled action code;
- a controlled outcome code;
- HTTP status;
- tenant, actor, and conversion-job HMAC fingerprints in separate domains;
- a numeric result count for list operations;
- low-cardinality deletion lifecycle counts and controlled failure codes.

It excludes raw tenant identifiers, raw subject identifiers, raw job UUIDs, claim signatures, permission headers, filenames, job messages, document text, storage paths, exception-controlled text, and artifact bytes. Job correlation uses a dedicated keyed HMAC domain so resource references cannot be joined directly with API paths, databases, support exports, or external telemetry. Retry provenance stores the actor-domain fingerprint rather than the source subject. Pseudonymized values remain personal data and inherit the retention, access, rotation, and incident-response requirements in `2026-08-04-audit-pseudonymization.md`.

Operators must distinguish the persisted receipt ledger from ordinary application logs. The ledger and aggregate metrics provide durable single-process cleanup evidence; logs are diagnostic only and never substitute for receipt state.

## Standalone and modular deployment

`ArtifactDeletionReceiptStore`, `ArtifactStore`, `ConversionJobRepository`, and the tenant-aware service contracts are replaceable boundaries. The reference filesystem ledger and artifact store permit standalone operation. naruon or another CWL host may replace them with shared durable adapters without changing the authorization, immutable identity, receipt state machine, or privacy-safe evidence contracts.

A modular deployment must preserve:

- signed tenant context before service access;
- tenant-scoped atomic mutation;
- permanent UUID or immutable generation identity;
- receipt-before-read ordering;
- one-way exact-digest binding before metadata tombstone;
- conversion/deletion generation fencing;
- idempotent retry and recovery;
- low-cardinality, privacy-safe evidence;
- fail-closed behavior when a required adapter cannot prove those invariants.

## Verification requirements

Automated tests must exercise the real signed-claim verifier and prove:

- absent and weak verifier keys make privileged endpoints unavailable before service access;
- missing, malformed, expired, and incorrectly signed claims fail before service access;
- missing `admin:read` or `admin:write` permissions fail before service access;
- list results contain only tenant-owned jobs for every dead-letter filter state;
- missing and cross-tenant delete/retry targets produce indistinguishable not-found responses;
- missing job identifiers fail closed without global lookup or mutation;
- delete and retry cross tenant-aware persistence boundaries without a separate unscoped lookup;
- compatibility adapters cannot reach global lookup, delete, or retry through tenant-aware defaults;
- exact same-object save is idempotent while distinct live or tombstoned UUID reuse is rejected;
- deletion removes the live job and secondary index but preserves UUID reservation;
- a colliding find-or-store candidate fails before index ownership changes;
- concurrent delete/save and retry/replacement interleavings preserve tenant and generation ownership;
- accepted retry provenance is a domain-separated keyed fingerprint;
- the durable deletion receipt is accepted before the first artifact read;
- first-read failure preserves metadata and restart-safe pending evidence rather than claiming confirmed absence;
- exact digest binding is one-way, durable, replay-validated, and required before metadata tombstone;
- successful cleanup, read failure, digest mismatch, delete failure, retry, restart, and bounded-batch recovery are deterministic;
- a permanently failing oldest receipt cannot starve newer cleanup across repeated bounded passes in one process;
- write-after-receipt and in-flight publication races cannot recreate deleted confidential bytes;
- cleanup evidence contains no raw UUID, tenant, path, digest dimension, exception text, or attached throwable;
- both signing keys are sourced from the shared config-tree mount;
- JaCoCo reports zero missed production lines and branches and public Javadocs pass with no warnings.

## References

Hu, V. C., Ferraiolo, D., Kuhn, D. R., Schnitzer, A., Sandlin, K., Miller, R., & Scarfone, K. (2014). *Guide to attribute based access control (ABAC) definition and considerations* (NIST Special Publication 800-162, updated August 2, 2019). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-162

OWASP Foundation. (2023). *API1:2023 broken object level authorization*. OWASP API Security Top 10. https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/

OWASP Foundation. (n.d.). *Authorization cheat sheet*. OWASP Cheat Sheet Series. Retrieved August 5, 2026, from https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
