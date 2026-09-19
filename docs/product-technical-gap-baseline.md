# Clearfolio Product–Technical Gap Baseline

Last code-current review: 2026-09-19
Reference branch: `sentinel/admin-endpoint-auth-1303568950222847238`
Reference head when this baseline was authored: `c8066ebe3fc110a50cb3854d997d76f8339ff131`

This document is a code-current gap ledger, not a release-readiness claim. Protected `main`, current pull-request heads, CI/security evidence, and immutable release artifacts remain authoritative when they differ from this snapshot.

## Product boundary and domain model

Clearfolio's current core subdomain is document conversion and preview. `ConversionJob` is the lifecycle aggregate used by the application service and worker. A job owns tenant/subject attribution, status, retry state, artifact reference, attempt count, and lifecycle timestamps. `TenantContext` is the verified request identity/authorization context that must be carried into every tenant-owned read or mutation boundary.

The current bounded-context responsibilities are:

- Controller/API: authenticate the request, require the endpoint permission, and pass the verified tenant context downstream. It must not implement cross-tenant filtering after a global query.
- Document conversion application service: enforce tenant ownership for job reads/mutations, coordinate validation/dedupe/queueing, and expose explicit lifecycle operations.
- Conversion job repository/state store: own persistence lookup and lifecycle transition boundaries. Durable implementations must combine tenant predicates and state transitions in the repository/application-service transaction rather than relying on a controller check followed by an unscoped mutation.
- Worker: claim/retry/complete jobs through `ConversionJobStateStore` and preserve recovery semantics.
- Artifact store: own converted PDF persistence/deletion independently from job metadata.

The shared kernel should remain limited to stable identifiers and contracts. Tenant/domain truth stays in Clearfolio; external identity or gateway systems supply verified claims but must not own `ConversionJob` semantics.

## Current verified implementation state

### Tenant authorization repair — implemented on the current PR branch, release evidence pending

Admin endpoints now distinguish `admin:read` and `admin:write`, retain the `TenantContext` returned by `TenantAccessService.require`, and pass it to tenant-aware application-service operations. Foreign/missing job mutations are intentionally collapsed to NOT_FOUND/404 semantics so an authenticated tenant cannot use the endpoint as an object-existence oracle. Retry attribution uses the verified subject identifier rather than a fixed `admin` label.

`DocumentConversionServiceTenantIsolationTest` fixes the service-contract invariant that tenant A cannot list, delete, or retry tenant B jobs. `AdminControllerTest` verifies that the controller uses the correct permission and carries the returned tenant context into list/delete/retry operations.

Status: **implemented, not release-ready**. Exact-head CI, SAST, CodeQL, security scan, fuzzing, and a current independent review must complete successfully before the PR can leave Draft. Evidence from predecessor heads is provenance only and is not inherited as current-head GREEN.

### Durable conversion-job persistence — designed, not implemented

The current runtime still uses `InMemoryConversionJobRepository`. The durable-persistence plan defines `conversion_jobs` plus append-only `conversion_job_events`, tenant/hash uniqueness, tenant/status indexes, retry-due indexes, explicit lifecycle transition events, restart recovery, and repository contract testing. No production SQL implementation is present yet.

Buyer-visible consequence: a process restart still loses job state, retry schedule, and process-local lifecycle evidence. A SQL profile cannot be called production-ready until job state, event persistence, artifact persistence, recovery, and tenant-scoped queries are exercised together.

## Priority gaps

| Priority | Gap | Current evidence | Required causal closure |
| --- | --- | --- | --- |
| P0 | Current admin-auth PR lacks terminal exact-head release evidence | PR branch contains tenant-scoped service/controller contracts and RED→GREEN tests; current checks/review are still required | Exact current SHA: compile/unit/integration + SAST + CodeQL + dependency/security scan + fuzz + independent review. Keep Draft until all required gates are terminal GREEN. |
| P0 | Job state and lifecycle trail are process-local | Durable repository plan explicitly states `InMemoryConversionJobRepository` remains the runtime implementation | Implement PostgreSQL schema/profile, repository contract tests, transaction-safe lifecycle events, restart/recovery tests, and rollback. Do not hold a DB transaction open across conversion/LLM/external I/O. |
| P0 | Artifact/job durability is not an atomic buyer recovery story | Artifact-store choice remains open while SQL job persistence is still planned | Define failure ordering and compensating/recovery semantics for job state vs artifact write/delete; prove crash/restart behavior with real storage. |
| P1 | Tenant-scoped list compatibility fallback reads the global snapshot | `DocumentConversionService.getAllJobs(TenantContext)` currently filters the compatibility `getAllJobs()` result | The durable repository must expose a tenant-predicate query and override this fallback. Add query-plan/index evidence and cross-tenant tests. |
| P1 | Tenant-aware retry compatibility method checks ownership before delegating to an unscoped transition | Safe for the current in-memory aggregate because tenant ownership is immutable, but it is not the target durable transaction model | Durable implementation must predicate retry acceptance on `(tenant_id, job_id)` in the same application-service/repository transaction as the state transition and event append. |
| P1 | Production OIDC/gateway claim validation remains outside the persistence slice | Durable-persistence plan lists production OIDC/JWT validation as external | Bind deployed authentication to a versioned identity/gateway contract; test missing/expired/forged claims, permission split, tenant substitution, and subject attribution at the HTTP boundary. |
| P1 | Lifecycle analytics/audit evidence is process-local | In-memory lifecycle event trail exists but cannot survive restart | Persist append-only events with purpose-bound fields, rebuild projections from the event trail, and verify no raw documents/tokens/converter stderr enter audit payloads. |
| P2 | Release and recovery evidence is not consolidated as an immutable buyer artifact | No claim is made here that a current immutable release exists | For the first protected release-ready head: version/CHANGELOG/tag/package, SBOM, provenance/attestation, reproducible build evidence, recovery drill, rollback procedure, and canonical immutable GitHub Release. |

## DDD and data invariants

The following invariants are release gates, not implementation suggestions:

1. A `ConversionJob` belongs to exactly one tenant for its lifetime. Tenant ownership is immutable.
2. Tenant-owned API reads and mutations are authorized by both permission and object ownership. A resource identifier alone never grants access.
3. Missing and foreign-tenant jobs are indistinguishable at externally observable object-access boundaries unless an explicitly documented administrative cross-tenant role is introduced later.
4. `findOrStoreByContentHash` deduplication is tenant-scoped; a hash match in another tenant is not a canonical match.
5. Each durable lifecycle transition updates current job state and appends the event that explains it in one bounded transaction. External conversion/artifact I/O must not execute while holding a long-lived database lock.
6. Retry acceptance is idempotent with respect to the aggregate's eligible state and preserves max-attempt/dead-letter policy.
7. Job/event persistence is normalized around authoritative job state plus append-only transition events. Analytics projections are derived read models, not cross-service SQL truth.
8. Security, performance, and durability claims require current-head executable evidence. A predecessor head's test or review result is not inherited after semantic changes.

## Acceptance slices

### Admin tenant isolation

RED fixtures must contain at least two tenants and exercise the real HTTP/controller→application-service path. Tenant A admin-read must never receive tenant B jobs; tenant A admin-write must not delete/retry a tenant B UUID; missing and foreign identifiers must share the same externally observable not-found behavior. Positive same-tenant list/delete/retry and missing/insufficient permission cases must remain covered.

### Durable persistence and recovery

Run the same repository contract suite against in-memory and PostgreSQL implementations. Exercise submitted, processing, succeeded, retry-scheduled, failed, dead-lettered, operator-retried, dedupe-hit, stale-processing recovery, crash between state/event operations, crash around artifact persistence, and restart reconstruction. Use real PostgreSQL for acceptance; synthetic data is appropriate only for focused unit tests.

### Buyer performance

When the durable API path exists, measure representative authenticated buyer requests with async load and k6/E2E. Report p50/p95/p99, error rate, DB query count/plan, connection-pool behavior, allocation/GC where applicable, and cold/warm conditions. Do not claim the fleet p95 ≤20 ms target from unit-test wall time or an unrealistically pre-warmed cache.

## Source traceability

- `src/main/java/com/clearfolio/viewer/controller/AdminController.java`
- `src/main/java/com/clearfolio/viewer/service/DocumentConversionService.java`
- `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`
- `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobRepository.java`
- `src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java`
- `src/test/java/com/clearfolio/viewer/service/DocumentConversionServiceTenantIsolationTest.java`
- `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
- `.jules/sentinel.md`

Update this baseline whenever those owner contracts, protected-head evidence, or release state changes materially.
