# Auth, RBAC, and Tenant Model

Date: 2026-07-02

This document defines the production authorization contract needed before
Clearfolio Viewer can claim tenant-safe preview access. It now includes the
first runtime enforcement slice: protected JSON APIs parse tenant headers,
check endpoint permissions, store job tenant metadata, filter tenant KPIs, and
hide cross-tenant jobs. It is not yet a production OIDC/JWT implementation, and
artifact reads still require the signed-link work described separately.

## Goal

Add a buyer-readable model for authentication, tenant isolation, permissions,
and audit scope so signed artifact links, durable metrics, and operator retry
can be implemented without guessing the security boundary later.

## Non-Goals

- Do not add local username/password login in the viewer service.
- Do not store refresh tokens in the current in-memory MVP.
- Do not create a separate auth library, submodule, or SDK yet.
- Do not claim production RBAC until validated token issuer, audience, expiry,
  revocation, and role mapping are implemented.

Ponytail decision: enterprise OIDC or S2S bearer tokens are the shortest useful
path. A custom login system would add buyer diligence risk without improving the
document-preview product.

## Identity Sources

| Caller | Authentication source | Expected use |
| --- | --- | --- |
| Browser user | Enterprise OIDC through the host platform or gateway | Opens viewer, creates artifact links, reads permitted artifacts. |
| Internal workflow | Service-to-service bearer token | Submits jobs and polls status from Power Platform or backend automation. |
| Operator | Enterprise OIDC plus operator role | Retries failed jobs, revokes artifact links, reviews evidence. |
| Buyer demo | Explicit demo tenant token or isolated demo profile | Shows the flow without mixing with production tenants. |

Current buyer-demo runtime headers:

- `X-Clearfolio-Tenant-Id: buyer-demo`
- `X-Clearfolio-Subject-Id: buyer-demo-operator`
- `X-Clearfolio-Permissions: job:create,job:read,job:retry,viewer:read,artifact-link:create,analytics:read`

These headers are a runtime enforcement scaffold, not a cryptographic identity
proof. Production must replace them with validated gateway/OIDC claims.

## Required Token Claims

| Claim | Required | Purpose |
| --- | --- | --- |
| `iss` | Yes | Trusted issuer allowlist. |
| `aud` | Yes | Must match `clearfolio-viewer-api` or `clearfolio-artifact`. |
| `sub` | Yes | User or service principal. |
| `tenantId` | Yes | Primary isolation boundary. |
| `roles` | Yes | Coarse-grained RBAC. |
| `scope` | Yes | Fine-grained API permissions. |
| `iat` | Yes | Audit timing. |
| `exp` | Yes | Short-lived access. |
| `jti` | Yes | Revocation and audit correlation. |
| `kid` | Yes for asymmetric tokens | Key rotation. |

Access tokens should be short-lived. Long-lived refresh token handling belongs
to the identity provider or gateway, not the viewer service.

## Roles and Permissions

| Role | Permissions |
| --- | --- |
| `viewer_user` | `job:create`, `job:read`, `viewer:read`, `artifact-link:create`, `artifact:read` |
| `workflow_client` | `job:create`, `job:read`, `viewer:read` |
| `operator` | `job:read`, `job:retry`, `artifact-link:revoke`, `audit:read` |
| `tenant_admin` | `job:read`, `artifact-link:revoke`, `audit:read`, `tenant:configure` |
| `buyer_reviewer` | `job:read`, `viewer:read`, `analytics:read`, `audit:read` in a demo or diligence tenant |

Server-side authorization must check both permission and tenant ownership. A
matching permission without matching `tenantId` is insufficient.

## Resource Ownership Rules

| Resource | Tenant binding | Access rule |
| --- | --- | --- |
| Conversion job | `job.tenantId` | Caller `tenantId` must match before status, viewer bootstrap, retry, or analytics drill-down. |
| Source document metadata | `document.tenantId` | Exposed only through job/viewer APIs after permission check. |
| Preview artifact | `artifact.tenantId` and `artifactChecksum` | Read only through short-lived signed artifact token. |
| Artifact link | `artifactLink.tenantId` and `tokenId` | Revocable by operator or tenant admin in the same tenant. |
| Metrics event | `event.tenantId` | Aggregate views must filter tenant unless explicitly buyer-demo scoped. |
| Audit event | `audit.tenantId` | Read by operator, tenant admin, or buyer reviewer for scoped evidence. |

Do not reveal whether a document exists in another tenant. Cross-tenant access
should return `404` for resource lookup or `403` for authenticated but
unauthorized action, depending on route semantics.

## API Enforcement Matrix

| API | Required permission | Tenant check |
| --- | --- | --- |
| `POST /api/v1/convert/jobs` | `job:create` | Assign job to caller `tenantId`. |
| `GET /api/v1/convert/jobs/{jobId}` | `job:read` | `job.tenantId == token.tenantId`. |
| `POST /api/v1/convert/jobs/{jobId}/retry` | `job:retry` | Same tenant plus operator role. |
| `GET /api/v1/viewer/{docId}` | `viewer:read` | `job.tenantId == token.tenantId`; artifact tokens are enforced in the signed-link slice. |
| `GET /viewer/{docId}` | none for HTML shell | Shell does not inspect job existence; protected JSON APIs decide state. |
| `POST /api/v1/viewer/{docId}/artifact-links` | `artifact-link:create` | Same tenant and succeeded job. |
| `GET /artifacts/{docId}.pdf` | `artifact:read` | Signed artifact token tenant and checksum must match. |
| `GET /api/v1/analytics/kpi-snapshot` | `analytics:read` | Tenant-scoped aggregate by default. |

Current implementation status:

- Implemented: `job:create`, `job:read`, `job:retry`, `viewer:read`, and
  `analytics:read` permission checks on JSON APIs.
- Implemented: `ConversionJob.tenantId` and `ConversionJob.subjectId`.
- Implemented: tenant-aware content-hash dedupe so two tenants do not collapse
  onto one canonical job for the same upload bytes.
- Implemented: cross-tenant status, retry, and viewer-bootstrap lookup returns
  `404` without revealing the other tenant's job.
- Implemented: KPI snapshots filter to the request tenant.
- Not implemented: OIDC/JWT signature, issuer, audience, expiry, revocation, and
  role mapping.
- Implemented: signed artifact link creation and artifact token verification
  for current in-memory PDF artifacts.
- Not implemented: durable artifact metadata, revocation, persisted artifact
  audit events, and production key management.

## Error Semantics

| Condition | Status | Error code |
| --- | ---: | --- |
| Missing token | 401 | `AUTH_TOKEN_REQUIRED` |
| Invalid token or signature | 401 | `AUTH_TOKEN_INVALID` |
| Expired token | 401 | `AUTH_TOKEN_EXPIRED` |
| Missing permission | 403 | `AUTH_FORBIDDEN` |
| Wrong tenant | 403 or 404 | `TENANT_RESOURCE_FORBIDDEN` |
| Revoked token | 401 | `AUTH_TOKEN_REVOKED` |
| Unknown issuer or audience | 401 | `AUTH_TOKEN_INVALID` |

Error payloads must keep the existing shared API shape and must not include raw
tokens or cross-tenant identifiers.

Current scaffold note: the shared `ApiExceptionHandler` emits HTTP status names
as `errorCode` values, so missing tenant headers currently return
`errorCode=UNAUTHORIZED` with message `auth token required`, and missing
permissions return `errorCode=FORBIDDEN`. Auth-specific error codes can replace
those once the OIDC/JWT validator is introduced.

## Audit Events

| Event | Required fields |
| --- | --- |
| `auth.accepted` | `tenantId`, `subjectId`, `roles`, `scopes`, `issuer`, `tokenId`, `traceId` |
| `auth.rejected` | `reason`, `issuer`, `audience`, `tokenFingerprint`, `traceId` |
| `job.created` | `tenantId`, `subjectId`, `jobId`, `contentHash`, `traceId` |
| `job.retry.requested` | `tenantId`, `operatorId`, `jobId`, `reason`, `traceId` |
| `artifact.link.created` | `tenantId`, `subjectId`, `docId`, `tokenId`, `expiresAt`, `traceId` |
| `artifact.link.revoked` | `tenantId`, `operatorId`, `tokenId`, `reason`, `traceId` |
| `artifact.read` | `tenantId`, `subjectId`, `docId`, `tokenId`, `rangeRequested`, `statusCode`, `traceId` |

Store token fingerprints, not raw tokens.

## Buyer Acceptance Criteria

- Every buyer-visible document, job, artifact, metric, and audit event has a
  tenant boundary.
- Every write or sensitive read has a server-side permission check.
- Artifact reads use signed artifact tokens, not bare `docId` capability URLs.
- Operator retry requires an operator permission and is auditable.
- KPI snapshots can be shown for one tenant without leaking another tenant's
  volume, latency, or failure rate.
- The demo environment can use an isolated `buyer-demo` tenant without weakening
  production policy.

## Implementation Sequence

1. Done: add request claim extraction from Clearfolio tenant headers for the
   buyer-demo runtime.
2. Done: add `tenantId`, `subjectId`, and permission checks to conversion job
   metadata and JSON API paths.
3. Done: enforce `job:create`, `job:read`, `job:retry`, `viewer:read`, and
   `analytics:read` on existing JSON routes.
4. Done: add tenant-scoped KPI projection from current in-memory jobs.
5. Next: replace demo headers with validated gateway/OIDC JWT claims.
6. Done: add signed artifact link creation and token verification.
7. Next: add durable revocation, persisted audit events, and CI/contract tests
   for production token rejection paths.

No library split is justified until a second Clearfolio service or external SDK
needs to reuse this authorization contract.
