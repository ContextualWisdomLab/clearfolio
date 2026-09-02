# Conversion Service Runtime Architecture

Last updated: 2026-09-03

This document contains detailed runtime-flow and persistence notes. The repository-level
component/context map is `ARCHITECTURE.md`; the code-current DDD, gap, remediation, and
evidence baseline is `docs/product-technical-gap-baseline.md`.

## Current implementation stance

- Web runtime stance: this implementation adopts Spring WebFlux (`spring-boot-starter-webflux`) over Servlet/MVC for request handling.
- Non-blocking contract stance: request handlers return quickly and conversion work is delegated to the worker queue.
- Tenant-admin stance: signed authentication claims and required permissions are verified at the HTTP edge. The Spring runtime fails closed with `503 Service Unavailable` when tenant-claim HMAC verifier material is unavailable; caller-supplied tenant/permission headers are not accepted as authorization in that state. Conversion-job ownership is enforced by tenant-scoped application-service/repository contracts rather than controller-local global filtering.
- Test compatibility boundary: `TenantAccessService` retains an explicit no-argument unsigned constructor for manually bound controller/unit tests; Spring runtime selects the `@Autowired` constructor and does not use that unsigned path.
- Audit identity stance: retry operator correlation uses a keyed, versioned, purpose-separated audit pseudonym; it is not authentication or authorization authority. Its current Spring-configured key source remains a known `AGENTS.md` KV/credential-registry deviation and is not merge-ready security architecture.
- Scope boundary: S2S preview-session orchestration is documented but still planned, not completed.

## Runtime flow

- Submit flow (`POST /api/v1/convert/jobs`): validation -> blocked-format policy evaluation (default block, optional auditable override headers) -> content hash dedupe -> enqueue async conversion -> return `202`.
- Status flow (`GET /api/v1/convert/jobs/{jobId}`): return lifecycle snapshot (`SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`) with retry metadata.
- Tenant-admin authorization precondition: parse request claims -> require runtime HMAC verifier material -> verify signed tenant/subject/permission claims and issue-time skew -> verify required permission. Missing runtime verifier material returns `503`; missing/invalid/expired signatures return `401`; missing permission returns `403`.
- Tenant-admin list flow (`GET /api/v1/admin/convert/jobs`): satisfy the authorization precondition for `JOB_READ` -> call tenant-scoped `DocumentConversionService.getJobsForTenant` -> optionally filter dead-letter state for presentation -> return only tenant-owned jobs.
- Tenant-admin delete flow (`DELETE /api/v1/admin/convert/jobs/{jobId}`): satisfy the authorization precondition for `JOB_DELETE` -> call tenant-scoped service delete -> map both missing and cross-tenant resources to `404` -> return `204` on deletion.
- Tenant-admin retry flow (`POST /api/v1/admin/convert/jobs/{jobId}/retry`): satisfy the authorization precondition for `JOB_RETRY` -> create privacy-safe retry audit identity -> call tenant-scoped application retry -> map missing/cross-tenant to `404`, ineligible to `409`, accepted to `202`.
- Worker startup recovery flow: on application readiness, select due `SUBMITTED` jobs and stale retryable `PROCESSING` jobs older than `conversion.processing-lease-timeout-ms`; re-enqueue due jobs directly and route stale processing jobs through retry scheduling before re-enqueue.
- Viewer UI flow (`GET /viewer/{docId}`): return HTML shell with mobile-safe loading/failed/ready states; when ready, embed PDF.js.
- Bootstrap flow (`GET /api/v1/viewer/{docId}` and `GET /api/v1/convert/viewer/{docId}`): return bootstrap JSON on `SUCCEEDED` with deterministic `sourceExtension`/`rendererAdapter`; return `409` for not-ready/failed states; return `404` when missing.
- Artifact flow (`GET /artifacts/{docId}.pdf`): serve converted PDF bytes for `SUCCEEDED` jobs only (single-range support).
- Health flow (`GET /healthz`): readiness probe.

## S2S delivery chain (documented target)

- `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`
- Current state: viewer endpoint contract is implemented in this repo; downstream S2S session orchestration remains planned and is documented in `docs/diagrams/preview-flow.md`.

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

Reference policy: `docs/engineering/acceptance-criteria.md`.

## Optional tracks

- client DB pooler
- PostgreSQL 17

## Runtime responsibility boundaries

- `controller`: delivery adapters only. They authenticate/authorize request permissions, validate transport inputs, invoke application ports, and map typed outcomes to HTTP. They do not own conversion aggregate authorization or cryptographic key mechanics.
- `service`: application orchestration, tenant-scoped conversion use cases, validation, policy-override exception lane, conversion execution/recovery.
- `repository`: conversion aggregate persistence and lifecycle-state ports, including tenant-scoped lookup/list contracts for externally addressable operations.
- `security` / `auth`: signed tenant claim verification, permission checks, and privacy-safe audit identity adapters. Production authorization fails closed when authoritative claim verification is unavailable. Audit correlation values do not become business approval or access authority.
- `model`: conversion-job aggregate lifecycle and retry/dead-letter invariants.
- `artifact`: converted/PDF-passthrough byte persistence and generation.
- `config`: typed non-secret runtime configuration and executor resources. Runtime secrets are required by `AGENTS.md` to resolve through a KV/credential registry; current direct Spring-configured HMAC inputs are migration debt, not the target architecture.

## Non-blocking, queue, and DB operation rules

- Request path non-blocking by default: heavy conversion runs in `DefaultConversionWorker`, not in API handlers.
- Queue flow in request path does not wait for completion; clients poll status endpoint.
- Queue policy baseline: bounded executor, retry scheduling with backoff, dead-letter fallback.
- DB/transaction policy (for future persistent DB phase): keep transactions short, avoid external calls inside transactions, use timeout/retry and `SKIP LOCKED` where applicable.
- Durable job repository target: keep `ConversionJobRepository` as the aggregate read/dedupe/tenant-scope boundary. `ConversionJobStateStore` is the explicit lifecycle transition boundary for worker claims, success, retry, dead-lettering, and operator retry acceptance before adding a SQL implementation.
- Tenant-native persistence target: durable adapters must push `findByTenantAndId` and `findAllByTenant` predicates into storage queries rather than materializing global rows and filtering at an external adapter.
- `ConversionJobRepository.findRecoverableJobs` and `DefaultConversionWorker` define the process-local startup recovery contract for due submitted jobs and stale processing leases. See `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`.
- Read-only routing policy (future DB phase): use provided read-only endpoint/DSN for read-biased traffic; strong consistency/DDL/lock-sensitive paths stay on primary.
- Pooler detection policy (best effort, future DB phase): in management DB `pgbouncer`/`pgcat`, try `SHOW VERSION;`; if detection fails, treat as `unknown` and keep safe fallback.
- Distributed Postgres compatibility policy: for Citus/Cosmos DB for PostgreSQL-style deployments, automatic read split is disabled by default and requires an explicit evidence-backed opt-in.

## OSS references (implementation and concept)

| OSS repo | License | Usage status | Trade-off note |
| --- | --- | --- | --- |
| `spring-projects/spring-framework` | Apache-2.0 | Implemented (WebFlux runtime) | Strong reactive stack, but requires careful blocking-code isolation. |
| `reactor/reactor-core` | Apache-2.0 | Implemented (reactive primitives) | Good async composition, but debugging stack traces can be harder than imperative flow. |
| `apache/tika` | Apache-2.0 | Removed from current runtime | Broad parser package was unused in production code and increased license-review surface; reconsider only with a narrow parser adapter and refreshed SBOM policy. |
| `jodconverter/jodconverter` | Apache-2.0 | Concept-only, not integrated | Useful LibreOffice bridge candidate for production conversion runtime. |
| `mozilla/pdf.js` | Apache-2.0 | Concept-only frontend reference | Stable PDF rendering baseline for unified viewer shell integration. |
| `ONLYOFFICE/DocumentServer` | AGPL-3.0 | Concept-only (import disallowed) | Architecture reference only; copyleft policy prevents direct dependency adoption. |

## Evidence pointers (file-level)

| Evidence target | File pointer |
| --- | --- |
| WebFlux dependency | `pom.xml` |
| Submit non-blocking controller path | `src/main/java/com/clearfolio/viewer/controller/ConversionController.java` |
| Tenant admin HTTP adapter | `src/main/java/com/clearfolio/viewer/controller/AdminController.java` |
| Signed tenant claims, fail-closed missing-verifier behavior, and permission checks | `src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java` |
| Tenant-scoped application contracts | `src/main/java/com/clearfolio/viewer/service/DocumentConversionService.java` |
| Tenant-scoped persistence contracts | `src/main/java/com/clearfolio/viewer/repository/ConversionJobRepository.java` |
| Retry audit identity privacy port/adapter | `src/main/java/com/clearfolio/viewer/security/RetryOperatorIdentityPort.java`, `src/main/java/com/clearfolio/viewer/security/HmacRetryOperatorIdentityAdapter.java` |
| Runtime secret/KV policy | `AGENTS.md` |
| Blocked-format override lane + audit signal | `src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java` |
| Override header contract | `src/main/java/com/clearfolio/viewer/service/PolicyOverrideRequest.java` |
| Conversion enqueue orchestration | `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java` |
| Worker retry/dead-letter behavior | `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java` |
| Bounded queue configuration | `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java` |
| NUL sanitization at persistence boundary | `src/main/java/com/clearfolio/viewer/model/ConversionJob.java` |
| Viewer adapter selection metadata | `src/main/java/com/clearfolio/viewer/api/ViewerBootstrapResponse.java` |
| Code-current product/DDD/gap baseline | `docs/product-technical-gap-baseline.md` |
| Mandatory gate evidence index | `docs/qa/evidence/LATEST.md` |
| Latest historical gate summary | `docs/qa/evidence/2026-02-21-ac-gates/SUMMARY.md` |

## Related design docs

- `ARCHITECTURE.md`
- `docs/product-technical-gap-baseline.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/submit-policy-adapter-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`
- `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
