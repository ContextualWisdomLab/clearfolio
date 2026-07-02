# Signed Artifact Link Design

Date: 2026-07-02

This document defines the production design for secure preview artifact access.
The current runtime now issues and verifies HMAC artifact tokens for in-memory
PDF artifacts, records issued token metadata in a runtime ledger, supports
tenant-scoped token revocation, records verified artifact reads as audit events,
and can replay that ledger from an optional local append-only file. Durable
artifact metadata, external audit persistence, object-store metadata, and
production key management are still implementation gaps.

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
- Requires a signed `artifactToken` query parameter or bearer artifact token.
- Verifies token scope, expiry, route `docId`, job tenant, and current artifact
  checksum before serving bytes.
- Records issued tokens in a runtime artifact link ledger.
- Blocks unknown or revoked token identifiers.
- Records verified artifact reads with tenant, subject, document, token id,
  range, status code, trace id, and timestamp.
- Uses in-memory PDF bytes and a runtime artifact ledger. Deployments can set
  `clearfolio.artifact-link-ledger.path` to persist issued-link, revocation,
  and read-audit events to a local append-only UTF-8 file and replay them on
  restart.
- The optional file-backed ledger is process-local evidence persistence, not a
  multi-replica production revocation table or object-store metadata layer.

This is stronger than the original MVP capability URL, but not yet enough for a
buyer production deployment without durable storage, externally persisted
revocation state, centralized audit, and object-store metadata.

## Proposed API Contract

### Create Artifact Link

```text
POST /api/v1/viewer/{docId}/artifact-links
Authorization: Bearer <access-token>
Content-Type: application/json
```

The current buyer-demo runtime uses `X-Clearfolio-*` tenant headers instead of a
validated bearer access token. Production must replace those headers with
validated gateway/OIDC claims.

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
- Link creation should emit an immutable `artifact.link.created` audit event
  once durable audit persistence exists.
- Query token exists only to support PDF.js `file=` loading; server-to-server
  callers should use `Authorization: Bearer <artifact-token>`.

### Revoke Artifact Link

```text
POST /api/v1/viewer/artifact-links/{tokenId}/revoke
Authorization: Bearer <access-token>
Content-Type: application/json
```

The current buyer-demo runtime requires `artifact-link:revoke` in
`X-Clearfolio-Permissions`.

Request:

```json
{
  "reason": "viewer closed"
}
```

Response:

```json
{
  "tokenId": "01J...",
  "revokedAt": "2026-07-02T07:05:00Z",
  "revokedBy": "user-123",
  "reason": "viewer closed",
  "revoked": true
}
```

Rules:

- Revocation is tenant-scoped and hides unknown or cross-tenant token ids as
  `404`.
- Repeating revocation is idempotent and returns the first recorded revocation
  fields.
- Revoked token ids are rejected before artifact bytes are served.

### Read Artifact Audit Events

```text
GET /api/v1/viewer/{docId}/artifact-read-events
Authorization: Bearer <access-token>
```

The current buyer-demo runtime requires `audit:read` and returns events filtered
to the caller tenant and document.

### Read Artifact

```text
GET /artifacts/{docId}.pdf?artifactToken=<token>
Range: bytes=0-65535
```

Current runtime validation:

1. Parse token and require valid HMAC or asymmetric signature.
2. Require `scope=artifact:read`.
3. Require `exp` in the future.
4. Require token `docId` to match the route `docId`.
5. Require token `tenantId` to match the artifact tenant.
6. Require token `artifactVersion` or `artifactChecksum` to match current
   artifact metadata.
7. Serve the PDF with the current range and no-store headers.

Production validation must additionally require `aud=clearfolio-artifact`,
persist revoked `jti` state outside process memory, and persist `artifact.read`
events without storing the raw token.

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

Current runtime token payload contains `jti`, `tenantId`, `sub`, `docId`,
`scope`, `purpose`, `artifactChecksum`, `iat`, and `exp`. `iss`, `aud`, and
revocation checks remain production-hardening work.

Signing options:

- **Base case:** HMAC-SHA-256 with a deployment secret stored in KMS or the
  platform secret manager.
- **Enterprise case:** asymmetric signing with key id `kid` and public-key
  verification support for edge deployments.

Do not put filenames, approval tokens, raw user claims, or source document
content in the token.

## Persistence Requirements

Current buyer-demo runtime persistence:

- Default mode keeps artifact link and read-event state in process memory.
- When `clearfolio.artifact-link-ledger.path` is configured, `ArtifactLinkLedger`
  appends `ISSUED`, `REVOKED`, and `READ` records to a local file.
- Text fields are Base64 URL-safe encoded and nullable fields use a sentinel so
  tenant ids, subjects, ranges, reasons, and trace ids can round-trip without
  ad hoc delimiter parsing.
- On startup, the ledger replays the file and rejects invalid or out-of-order
  lines rather than silently accepting corrupted revocation or audit state.

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
- Existing `/viewer/{docId}` and JSON bootstrap contracts remain additive:
  `artifactLinkUrl`, `artifactLinkExpiresAt`, and `artifactLinkScope` are now
  returned by the protected viewer bootstrap API.

## Implementation Sequence

1. Done: implement the auth and tenant model defined in
   `docs/security/2026-07-02-auth-tenant-model.md`.
2. Done: add stateless checksum binding against current in-memory artifact
   bytes.
3. Done: add `POST /api/v1/viewer/{docId}/artifact-links`.
4. Done: add token verification to artifact serving.
5. Done: add PDF.js viewer bootstrap support for signed links.
6. Done: add runtime token ledger, token revocation, and read audit API.
7. Next: add durable artifact metadata with checksum and tenant id.
8. Next: persist revocation and access audit events outside process memory.
9. Next: remove direct unsigned fallback paths from production profiles.

No separate library or submodule is justified yet. Extract token signing only
after a second runtime or SDK needs the same contract.
