# Clearfolio API and Integration Contract

Status: Canonical API authority index
Baseline: protected `main` at `55d7ae8647208e301f282350f076eeddaba61d11`

This document is an index and compatibility contract. Source controllers and versioned OpenAPI artifacts remain executable schema authority. Active-PR behavior is labelled explicitly.

## Contract principles

- public URLs and payloads change only through reviewed compatibility analysis;
- tenant/permission authority is enforced server-side;
- cross-tenant resource existence is concealed where required;
- document-byte delivery requires the artifact authority defined in ADR-0002;
- asynchronous job APIs do not wait for conversion completion;
- errors use controlled response shapes and do not expose internal exception details;
- hosts such as naruon integrate over versioned interfaces rather than application-database access.

## Current endpoint matrix

| Endpoint | Authority | Maturity / notes |
| --- | --- | --- |
| `POST /api/v1/convert/jobs` | tenant `job:create` + validation/policy boundary | `IMPLEMENTED_ON_MAIN`; returns async job contract |
| `GET /api/v1/convert/jobs/{jobId}` | tenant job read + same-tenant ownership | `IMPLEMENTED_ON_MAIN` |
| `POST /api/v1/convert/jobs/{jobId}/retry` | retry permission + same-tenant/dead-letter eligibility | `IMPLEMENTED_ON_MAIN`; stricter signed-claim/admin contracts evolve in active stack |
| `DELETE /api/v1/convert/jobs/{jobId}` | delete permission + same tenant | present on current code path; durable deletion semantics `ACTIVE_PR` #268 |
| `GET /api/v1/viewer/{docId}` | viewer permission + same tenant | `IMPLEMENTED_ON_MAIN`; returns bootstrap only for succeeded job |
| `GET /api/v1/convert/viewer/{docId}` | same as canonical viewer bootstrap | compatibility alias |
| `POST /api/v1/viewer/{docId}/artifact-links` | artifact-link-create permission + same tenant | `IMPLEMENTED_ON_MAIN`; returns signed short-lived URL/token metadata |
| `POST /api/v1/viewer/artifact-links/{tokenId}/revoke` | artifact-link-revoke permission + tenant-bound token record | `IMPLEMENTED_ON_MAIN` |
| `GET /api/v1/viewer/{docId}/artifact-read-events` | audit-read permission + tenant scope | `IMPLEMENTED_ON_MAIN` |
| `GET /artifacts/{docId}.pdf` | signed artifact token; token scope/doc/tenant/checksum/expiry/ledger/revocation | `IMPLEMENTED_ON_MAIN`; zero or one Range; controlled read audit |
| `GET /api/v1/convert/jobs/{jobId}/download` | tenant `artifact:read` + same tenant + signed artifact delivery | `IMPLEMENTED_ON_MAIN`; protected-main #270 requires signed artifact token verification, zero-or-one Range handling, checksum binding and verified-read audit |
| admin list/retry/delete surfaces | signed tenant claims + least-privilege admin permission + tenant-scoped service contract | stronger contract `ACTIVE_PR` #268 |
| `GET /healthz` | orchestration probe | protected-main health surface; liveness semantic target `ACTIVE_PR` #295 |
| `GET /readyz` | orchestration traffic-readiness probe | `ACTIVE_PR` #295 only; not protected-main behavior |

## Artifact delivery contract

An authorized document-byte read must satisfy all applicable layers:

1. tenant/actor permission for the endpoint;
2. same-tenant resource ownership/concealment;
3. succeeded conversion/artifact existence;
4. signed artifact token presence and signature validity;
5. non-expired `artifact:read` scope;
6. route document identity match;
7. issued-token ledger presence and not revoked;
8. tenant/job binding;
9. current artifact checksum match;
10. zero or one valid HTTP byte range;
11. controlled read-audit recording for verified-token reads.

A valid tenant permission does not waive token/revocation/audit checks. A token does not waive endpoint tenant permission where the endpoint requires it.

## Asynchronous lifecycle contract

Public job state is currently:

- `SUBMITTED`
- `PROCESSING`
- `SUCCEEDED`
- `FAILED`

Retry exhaustion remains `FAILED` plus dead-letter evidence. Clients must not invent a separate terminal enum unless a versioned API change introduces one.

A valid submit returns an accepted response and a status URL. Heavy conversion remains outside the request completion path.

## Error semantics

Errors use the repository's stable API error response fields (`errorCode`, compatibility `code` where present, `message`, `traceId`, `details`). Internal paths, secrets, token content, document bytes, raw tenant identifiers beyond required contract fields, and uncontrolled exceptions are not valid error details.

Authorization status can intentionally distinguish:

- authentication absent/invalid → controlled unauthorized;
- permission denied → controlled forbidden when disclosure is safe;
- cross-tenant or concealed resource → not found;
- valid resource not ready → conflict or other endpoint-specific controlled state;
- invalid range → range-not-satisfiable.

## Versioning and compatibility

Protected `main` currently identifies its shipped HTTP contract through the `/api/v1/**` path namespace. It does **not** yet implement a separate request/response API-version negotiation header.

PR #379 is `ACTIVE_PR` evidence for a bounded negotiation contract and must not be represented as shipped until protected merge. Its proposed behavior keeps existing `/api/v1/**` callers backward compatible when `X-Clearfolio-Api-Version` is absent or blank, accepts an explicit exact `v1`, declares the current `v1` version on API responses, and rejects any explicitly unsupported version with a controlled error before controller dispatch. The exact active-PR head and its checks/reviews remain non-transferable evidence and must be revalidated before maturity changes.

Breaking changes include removing/renaming public endpoints or fields, changing tenant/permission semantics in a way that makes previously authorized data inaccessible without a security reason/migration, changing lifecycle state meanings, changing artifact-token claim interpretation, or changing error semantics relied on by clients.

Security tightening that closes an unsafe bypass may intentionally reject previously accepted requests. Such changes require migration notes and updated client/contract tests rather than preserving an insecure compatibility path.

## naruon / MSA composition

A host may:

- submit documents under an explicitly trusted tenant/actor mapping;
- poll status or receive future versioned callbacks;
- request viewer bootstrap/signed artifact links;
- surface Clearfolio failure/recovery state to its own UI.

A host may not:

- read/write Clearfolio application persistence directly;
- mint Clearfolio artifact claims without delegated reviewed authority;
- bypass Clearfolio format/security validation;
- reinterpret placeholder output as supported-format fidelity;
- merge/release Clearfolio because its own model or status says the product is ready.

## OpenAPI synchronization

`docs/deployment/clearfolio-buyer-connector.openapi.yaml` is an existing integration artifact. It must be compared with current controllers and this contract whenever endpoints/permissions/payloads change. Until a single generated/openapi-tested schema covers every current endpoint, the OpenAPI surface is `PARTIAL`, not a complete product specification.

## Contract tests

Required changes to public API authority should include:

- controller/serialization tests;
- tenant/permission and cross-tenant tests;
- malformed/edge input tests;
- artifact token/range/revocation/audit tests for byte delivery;
- OpenAPI/schema compatibility tests where the path is represented;
- naruon/integration contract tests when a versioned host interface is published.
