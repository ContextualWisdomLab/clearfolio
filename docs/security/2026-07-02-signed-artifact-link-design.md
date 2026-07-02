# Signed Artifact Link Design

Date: 2026-07-02

This document defines the production design for secure preview artifact access.
It is intentionally a design artifact, not an implementation claim. Current
runtime artifact access is still gated only by `docId` and job status.

## Goal

Replace capability-style artifact reads such as `/artifacts/{docId}.pdf` with
short-lived, tenant-bound, auditable signed links before production use.

The design must support PDF.js browser viewing, server-to-server embedding, and
operator audit without adding a separate library or submodule yet.

## Current Runtime Boundary

Current route:

```text
GET /artifacts/{docId}.pdf
```

Current controls:

- Returns only when the job exists and status is `SUCCEEDED`.
- Supports one HTTP byte range.
- Returns `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.
- Uses in-memory PDF bytes and has no tenant, expiry, revocation, or signature.

This is acceptable for the MVP demo but not for a buyer production deployment.

## Proposed API Contract

### Create Artifact Link

```text
POST /api/v1/viewer/{docId}/artifact-links
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```json
{
  "purpose": "viewer-preview",
  "ttlSeconds": 300,
  "viewerSessionId": "optional-session-id"
}
```

Response:

```json
{
  "artifactUrl": "/artifacts/0f2b...c91.pdf?artifactToken=eyJ...",
  "expiresAt": "2026-07-02T06:50:00Z",
  "tokenId": "01J...",
  "scope": "artifact:read",
  "docId": "0f2b...c91"
}
```

Rules:

- `ttlSeconds` defaults to 300 and is capped at 900.
- `docId` must belong to the caller's `tenantId`.
- Job status must be `SUCCEEDED`.
- Token scope must be `artifact:read`.
- Link creation emits an immutable `artifact.link.created` audit event.
- Query token exists only to support PDF.js `file=` loading; server-to-server
  callers should use `Authorization: Bearer <artifact-token>`.

### Read Artifact

```text
GET /artifacts/{docId}.pdf?artifactToken=<token>
Range: bytes=0-65535
```

Validation:

1. Parse token and require valid HMAC or asymmetric signature.
2. Require `aud=clearfolio-artifact`.
3. Require `scope=artifact:read`.
4. Require `exp` in the future.
5. Require token `docId` to match the route `docId`.
6. Require token `tenantId` to match the artifact tenant.
7. Require token `artifactVersion` or `artifactChecksum` to match current
   artifact metadata.
8. Reject revoked `jti`.
9. Serve the PDF with the current range and no-store headers.
10. Emit an `artifact.read` metric event without storing the token.

## Token Shape

Recommended claims:

| Claim | Required | Purpose |
| --- | --- | --- |
| `jti` | Yes | Revocation and audit correlation. |
| `iss` | Yes | Clearfolio deployment issuer. |
| `aud` | Yes | Must equal `clearfolio-artifact`. |
| `sub` | Yes | User, service principal, or viewer session. |
| `tenantId` | Yes | Tenant boundary. |
| `docId` | Yes | Artifact binding. |
| `artifactChecksum` | Yes | Prevents stale token reuse after artifact rewrite. |
| `scope` | Yes | Must contain `artifact:read`. |
| `iat` | Yes | Audit timestamp. |
| `exp` | Yes | Short expiry. |
| `purpose` | Yes | `viewer-preview`, `download`, or `integration`. |

Signing options:

- **Base case:** HMAC-SHA-256 with a deployment secret stored in KMS or the
  platform secret manager.
- **Enterprise case:** asymmetric signing with key id `kid` and public-key
  verification support for edge deployments.

Do not put filenames, approval tokens, raw user claims, or source document
content in the token.

## Persistence Requirements

Minimum tables when durable storage exists:

```text
artifact_links(
  token_id,
  tenant_id,
  doc_id,
  artifact_checksum,
  subject_id,
  purpose,
  scope,
  issued_at,
  expires_at,
  revoked_at,
  revoked_by,
  created_trace_id
)
```

```text
artifact_access_events(
  event_id,
  token_id,
  tenant_id,
  doc_id,
  subject_id,
  range_requested,
  status_code,
  served_bytes,
  occurred_at,
  trace_id
)
```

Short-lived stateless tokens can serve the artifact without a lookup, but the
revocation table must be checked for enterprise tenants and incident response.

## Failure Semantics

| Condition | Status | Error code |
| --- | ---: | --- |
| Missing token | 401 | `ARTIFACT_TOKEN_REQUIRED` |
| Invalid signature | 401 | `ARTIFACT_TOKEN_INVALID` |
| Expired token | 401 | `ARTIFACT_TOKEN_EXPIRED` |
| Wrong tenant or subject lacks access | 403 | `ARTIFACT_FORBIDDEN` |
| Wrong doc id binding | 403 | `ARTIFACT_FORBIDDEN` |
| Job missing or not succeeded | 404 | `ARTIFACT_NOT_FOUND` |
| Invalid range | 416 | Existing range response |

Do not reveal whether a different tenant owns the document.

## Buyer Acceptance Criteria

- A buyer can see that artifact URLs expire and are scoped to tenant, document,
  artifact checksum, and read purpose.
- PDF.js can still load the artifact with byte ranges.
- Operators can revoke a link by `tokenId`.
- Audit and KPI systems can count link creation and artifact reads without
  storing the signed token.
- Existing `/viewer/{docId}` and JSON bootstrap contracts can remain additive:
  add `artifactLinkUrl`, `artifactLinkExpiresAt`, and `artifactLinkScope` only
  after auth/tenant runtime exists.

## Implementation Sequence

1. Add auth and tenant model design.
2. Add durable artifact metadata with checksum and tenant id.
3. Add `POST /api/v1/viewer/{docId}/artifact-links`.
4. Add token verification to artifact serving.
5. Add revocation and access audit tables.
6. Add PDF.js viewer bootstrap support for signed links.
7. Remove direct unsigned artifact access from production profiles.

No separate library or submodule is justified yet. Extract token signing only
after a second runtime or SDK needs the same contract.
