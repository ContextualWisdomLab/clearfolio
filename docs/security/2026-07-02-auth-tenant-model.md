# Auth, RBAC, and Tenant Model

Date: 2026-07-02

This document defines the production authorization contract needed before
Clearfolio Viewer can claim tenant-safe preview access. It is a design artifact,
not an implementation claim. The current MVP runtime is still unauthenticated
and single-tenant.

## Goal

Add a buyer-readable model for authentication, tenant isolation, permissions,
and audit scope so signed artifact links, durable metrics, and operator retry
can be implemented without guessing the security boundary later.

## Non-Goals

- Do not add local username/password login in the viewer service.
- Do not store refresh tokens in the current in-memory MVP.
- Do not create a separate auth library, submodule, or SDK yet.
- Do not claim production RBAC until server-side enforcement exists.

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
| `GET /api/v1/viewer/{docId}` | `viewer:read` | `artifact.tenantId == token.tenantId`. |
| `GET /viewer/{docId}` | `viewer:read` | Server creates scoped bootstrap only after auth. |
| `POST /api/v1/viewer/{docId}/artifact-links` | `artifact-link:create` | Same tenant and succeeded job. |
| `GET /artifacts/{docId}.pdf` | `artifact:read` | Signed artifact token tenant and checksum must match. |
| `GET /api/v1/analytics/kpi-snapshot` | `analytics:read` | Tenant-scoped aggregate by default. |

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

1. Add request principal extraction from validated gateway/OIDC JWT claims.
2. Add `tenantId`, `subjectId`, and `permissions` to conversion job metadata.
3. Enforce `job:create`, `job:read`, and `viewer:read` on existing routes.
4. Add `job:retry` for operator retry.
5. Add signed artifact link creation and token verification.
6. Add tenant-scoped KPI and audit projections.
7. Add CI or contract tests proving cross-tenant denial.

No library split is justified until a second Clearfolio service or external SDK
needs to reuse this authorization contract.
