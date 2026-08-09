# Auth, RBAC, and Tenant Model

Date: 2026-07-02
Last updated: 2026-08-09

This document defines the production authorization contract needed before
Clearfolio Viewer can claim tenant-safe preview access. It now includes the
first runtime enforcement slice: protected JSON APIs parse tenant headers,
check endpoint permissions, store job tenant metadata, filter tenant KPIs, and
hide cross-tenant jobs. It also includes optional gateway-signed tenant header
validation with HMAC and timestamp skew controls when
`clearfolio.tenant-claims.hmac-secret` is configured. The `production` Spring
profile now fails closed when that secret is missing, so unsigned tenant headers
cannot be accidentally promoted as a production boundary. It is not yet a
production OIDC/JWT implementation.

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
- `X-Clearfolio-Permissions: job:create,job:read,job:retry,viewer:read,artifact:read,artifact-link:create,analytics:read`

These headers are a runtime enforcement scaffold. In unsigned demo mode they
are not a cryptographic identity proof. When
`clearfolio.tenant-claims.hmac-secret` is set, the service also requires:

- `X-Clearfolio-Claims-Issued-At: <epoch-second>`
- `X-Clearfolio-Claims-Signature: <base64url-hmac-sha256>`

The signed payload is:

```text
tenantId
subjectId
canonicalPermissions
issuedAt
```

The default clock-skew window is 300 seconds and can be set with
`clearfolio.tenant-claims.max-skew-seconds`. This closes the immediate
gateway-to-service spoofing gap for tenant headers, but production should still
replace the scaffold with validated gateway/OIDC claims.

Production profile boundary:

- `SPRING_PROFILES_ACTIVE=production` requires
  `clearfolio.tenant-claims.hmac-secret`.
- Missing or blank secret fails application startup through
  `ProductionAuthReadinessConfig`.
- This prevents unsigned local demo headers from being used as a production
  claim boundary; it does not replace OIDC/JWT issuer, audience, expiry, and
  role validation.

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

Artifact-byte authorization deliberately has two independent layers when the
endpoint contract requires them:

1. tenant authorization (`artifact:read` plus same-tenant ownership), and
2. signed artifact-delivery authority (signature, expiry, `artifact:read` scope,
   document/tenant/checksum binding, issued-token ledger, revocation, canonical
   single-Range handling, and controlled read-audit evidence).

Possessing the tenant permission does not bypass the signed token boundary, and
possessing a signed token does not bypass an endpoint's tenant authorization.

## Resource Ownership Rules

| Resource | Tenant binding | Access rule |
| --- | --- | --- |
| Conversion job | `job.tenantId` | Caller `tenantId` must match before status, direct download, viewer bootstrap, retry, or analytics drill-down. |
| Source document metadata | `document.tenantId` | Exposed only through job/viewer APIs after permission check. |
| Preview artifact | `artifact.tenantId` and `artifactChecksum` | Read through the short-lived signed artifact-delivery contract. The direct job-download route additionally requires dedicated `artifact:read` and matching job tenant before artifact-store access, then validates signature, expiry, scope, document/tenant/checksum binding, issuance and revocation before returning bytes. |
| Artifact link | `artifactLink.tenantId` and `tokenId` | Revocable by operator or tenant admin in the same tenant. |
| Metrics event | `event.tenantId` | Aggregate views must filter tenant unless explicitly buyer-demo scoped. |
| Audit event | `audit.tenantId` | Read by operator, tenant admin, or buyer reviewer for scoped evidence. |

Do not reveal whether a document exists in another tenant. Cross-tenant access
should return `404` for resource lookup or `403` for authenticated but
unauthorized action, depending on route semantics.

## API Enforcement Matrix

| API | Required permission / signed authority | Tenant and artifact checks |
| --- | --- | --- |
| `POST /api/v1/convert/jobs` | `job:create` | Assign job to caller `tenantId`. |
| `GET /api/v1/convert/jobs/{jobId}` | `job:read` | `job.tenantId == token.tenantId`. |
| `GET /api/v1/convert/jobs/{jobId}/download` | tenant `artifact:read` **and** valid signed artifact token | Verify dedicated tenant permission before job lookup; conceal tenant mismatch as `404`; require succeeded job and artifact; verify token signature, expiry, scope, route document id, tenant/job binding, current checksum, issued-token ledger, and revocation; support zero or one Range; record verified full/partial/rejected-range read audit. |
| `POST /api/v1/convert/jobs/{jobId}/retry` | `job:retry` | Same tenant plus operator role. |
| `GET /api/v1/viewer/{docId}` | `viewer:read` | `job.tenantId == token.tenantId`; bootstrap issues signed artifact link for ready jobs. |
| `GET /viewer/{docId}` | none for HTML shell | Shell does not inspect job existence; protected JSON APIs decide state. |
| `POST /api/v1/viewer/{docId}/artifact-links` | `artifact-link:create` | Same tenant and succeeded job. |
| `GET /artifacts/{docId}.pdf` | valid signed artifact token | Signed token scope/document/tenant/current checksum/issuance/revocation must match; zero or one Range; record read audit. |
| `GET /api/v1/analytics/kpi-snapshot` | `analytics:read` | Tenant-scoped aggregate by default. |

## Current Branch Implementation Status

The bullets in this section describe the current branch under review. They do
not become protected-main release evidence until the unchanged exact head passes
all repository gates and integrates.

- Implemented: `job:create`, `job:read`, `job:retry`, `viewer:read`,
  `artifact:read`, and `analytics:read` permission checks on JSON APIs.
- Implemented: direct conversion-job downloads validate dedicated
  `artifact:read` before resource lookup, enforce same-tenant ownership before
  artifact-store access, and conceal cross-tenant UUID access as `404`.
- Implemented on the current branch: direct downloads now reuse
  `ArtifactLinkService` signed-delivery verification rather than returning bytes
  on tenant permission alone. Missing/invalid tokens fail closed, revoked tokens
  are rejected, token scope/document/tenant/current-checksum/issuance state is
  validated, zero-or-one Range semantics are shared with canonical artifact
  delivery, and verified full/partial/rejected-range reads emit controlled audit
  evidence.
- Implemented: `ConversionJob.tenantId` and `ConversionJob.subjectId`.
- Implemented: tenant-aware content-hash dedupe so two tenants do not collapse
  onto one canonical job for the same upload bytes.
- Implemented: cross-tenant status, direct download, retry, and viewer-bootstrap
  lookup returns `404` without revealing the other tenant's job.
- Implemented: KPI snapshots filter to the request tenant.
- Implemented: optional HMAC validation for gateway-signed tenant headers when
  `clearfolio.tenant-claims.hmac-secret` is configured.
- Implemented: `production` Spring profile startup fails when signed tenant
  claim secret is missing.
- Not implemented: production OIDC/JWT signature, issuer, audience, expiry,
  revocation, and role mapping.
- Implemented: signed artifact link creation and artifact token verification
  for current PDF artifacts.
- Implemented: runtime artifact token ledger, tenant-scoped token revocation,
  and artifact read audit-event API.
- Not implemented: durable distributed artifact metadata/revocation/audit state
  and production external key-management integration.

## Artifact Delivery Failure Semantics

| Condition | Status | Contract |
| --- | ---: | --- |
| Missing signed artifact token | 401 | Fail before document bytes are returned. |
| Invalid signature/structure/expiry | 401 or controlled token status | Do not disclose token internals. |
| Missing tenant permission | 403 | Fail before resource lookup where the endpoint requires permission. |
| Wrong tenant | 404 concealment on resource lookup | Do not reveal cross-tenant document existence. |
| Revoked issued artifact token | 403 on the current artifact-token contract | Preserve revocation without falling back to tenant permission. |
| Token document/tenant/checksum mismatch | controlled 401/403 | Current artifact integrity and ownership must match the signed claim. |
| Valid single Range | 206 | Return one bounded slice with `Content-Range` and `Accept-Ranges`. |
| Invalid, multi-range, or unsatisfiable range | 416 under Clearfolio's deliberately narrow range profile | Do not silently serve a whole artifact. |
| Unknown OIDC issuer/audience (future IdP path) | 401 | Production identity integration remains planned. |

Error payloads must keep the existing shared API shape and must not include raw
tokens or cross-tenant identifiers.

Current scaffold note: the shared `ApiExceptionHandler` emits HTTP status names
as `errorCode` values for tenant-claim failures. Artifact-token delivery returns
controlled low-information HTTP failures and does not echo token content.

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

Store token fingerprints or controlled token identifiers, not raw tokens.

## Buyer Acceptance Criteria

- Every buyer-visible document, job, artifact, metric, and audit event has a
  tenant boundary.
- Every write or sensitive read has a server-side permission check.
- Artifact reads use signed artifact tokens, not bare `docId` capability URLs.
- Direct conversion-job downloads require authenticated `artifact:read`,
  same-tenant ownership, a valid non-revoked signed artifact token bound to the
  current artifact checksum, the canonical zero-or-one Range profile, and
  controlled read-audit evidence; `job:read` or `artifact:read` alone never
  authorizes document bytes.
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
3. Done: enforce `job:create`, `job:read`, `job:retry`, `viewer:read`,
   `artifact:read`, and `analytics:read` on existing JSON routes.
4. Done: add tenant-scoped KPI projection from current in-memory jobs.
5. Done: add optional gateway-signed tenant headers with HMAC and timestamp
   skew controls.
6. Done: fail closed for `production` profile when the tenant-claim signing
   secret is absent.
7. Next: replace demo headers with validated gateway/OIDC JWT claims.
8. Done: add signed artifact link creation, issued-token ledger, revocation,
   current-artifact checksum binding, Range handling, and read auditing.
9. Done on the current branch: route direct conversion-job downloads through the
   same signed artifact-delivery authority while preserving dedicated tenant
   `artifact:read` and cross-tenant `404` concealment.
10. Next: move token issuance/revocation/read-audit and job lifecycle evidence
    from process/local-ledger boundaries to the reviewed durable distributed
    persistence design; add production key-management integration and end-to-end
    IdP rejection contracts.

No library split is justified until a second Clearfolio service or external SDK
needs to reuse this authorization contract.
