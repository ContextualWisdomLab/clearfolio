# Clearfolio Viewer

This repository contains the MVP backend for an integrated document viewer platform.
The current implementation includes non-blocking submit, job status polling, and
asynchronous conversion that produces an in-memory PDF artifact for preview.

## Quick Start

1. Build and verify compilation:
   - `mvn -DskipTests compile`
2. Run tests:
   - `mvn test`
3. Start the app locally:
   - `mvn spring-boot:run`
4. Check readiness:
   - `curl -sS http://localhost:8080/healthz`

## Scope

- `GET /`: buyer-demo upload, status, KPI, KPI snapshot evidence,
  operator recovery evidence, and session-history shell.
- `POST /api/v1/convert/jobs`: upload document and receive async job id.
- `GET /api/v1/convert/jobs/{jobId}`: poll conversion status and lifecycle fields.
- `POST /api/v1/convert/jobs` response includes `jobId`, `status`, and `statusUrl`.
- `POST /api/v1/convert/jobs/{jobId}/retry`: operator-triggered retry for dead-lettered jobs.
- `GET /viewer/{docId}`: canonical HTML viewer UI entrypoint (mobile-safe loading/failed/ready states).
- `GET /api/v1/viewer/{docId}` and `GET /api/v1/convert/viewer/{docId}`: viewer bootstrap JSON with a short-lived signed artifact URL.
- `POST /api/v1/viewer/{docId}/artifact-links`: create a tenant-bound signed artifact URL for succeeded jobs.
- `GET /api/v1/analytics/kpi-snapshot`: current conversion KPI counters and optional snapshot evidence for demo and diligence evidence.
- `GET /api/v1/analytics/kpi-snapshot-exports`: tenant-scoped exported KPI snapshot evidence.
- `GET /artifacts/{docId}.pdf`: serves converted PDF bytes (SUCCEEDED jobs only) with single-range support after artifact token verification.
- Errors follow shared shape (`errorCode`, optional `code`, `message`, `traceId`, `details`) for 404/409/400/500 paths.
- `GET /healthz`: readiness probe.
- HWP/HWPX are blocked by configuration.

Protected JSON APIs require Clearfolio tenant headers in the current buyer-demo
runtime: `X-Clearfolio-Tenant-Id`, `X-Clearfolio-Subject-Id`, and
`X-Clearfolio-Permissions`. The built-in demo shell sends `buyer-demo` headers
automatically. These headers are a runtime enforcement scaffold, not production
OIDC/JWT validation. Deployments can set
`clearfolio.tenant-claims.hmac-secret` to require gateway-signed tenant headers
with `X-Clearfolio-Claims-Issued-At` and `X-Clearfolio-Claims-Signature`;
validated OIDC/JWT issuer, audience, expiry, revocation, and role mapping remain
production gaps. Buyer sandbox deployments should use the `buyer-demo` Spring
profile and follow
`docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`.

## Compatibility notes

- API contract has been kept backward-compatible with the existing jobs + viewer flow.
- `GET /viewer/{docId}` remains the canonical entry route, but now serves HTML (PDF.js viewer).
- Alias endpoints remain stable, with signed artifact link fields added to
  viewer bootstrap responses.
- Dead-letter terminal cases keep `status=FAILED` in API payloads and set
  `deadLettered=true` when retries are exhausted.
- Dead-lettered jobs can be re-queued by an operator with
  `X-Clearfolio-Operator-Id` via `/api/v1/convert/jobs/{jobId}/retry`.
- The buyer-demo shell summarizes session-scoped operator recovery evidence:
  needs-action documents, retry-ready dead-lettered jobs, last accepted retry,
  and latest inspected job detail. This is demo evidence, not a production admin
  console.
- Status, viewer bootstrap, retry, and KPI JSON APIs enforce tenant permission
  headers and hide cross-tenant jobs as `404`.
- Tenant headers can be HMAC-signed by a trusted gateway when
  `clearfolio.tenant-claims.hmac-secret` is configured; unsigned local demo mode
  should not be exposed as a production internet boundary.
- `GET /viewer/{docId}` returns an HTML shell without checking job existence;
  the protected JSON APIs determine visible state.
- Artifact reads now require a signed `artifactToken` query parameter or bearer
  token. Issued tokens are recorded in a runtime ledger, can be revoked by
  `tokenId`, and successful artifact reads are exposed as tenant-scoped audit
  evidence. Deployments can set `clearfolio.artifact-link-ledger.path` to
  replay issued-link, revocation, and read-audit metadata from a local
  append-only ledger file after restart. Centralized durable persistence and
  object-store metadata remain open.
- Deployments can set `clearfolio.analytics-snapshot-ledger.path` to append
  exported KPI snapshots to a local file and replay them after a single-process
  restart. `GET /api/v1/analytics/kpi-snapshot-exports` exposes the same
  evidence to authorized tenant callers, and the buyer-demo shell renders the
  latest export count, subject, export time, and runtime job count without
  showing tenant ids. This is buyer-demo evidence continuity, not the full
  durable metrics event stream described in
  `docs/analytics/2026-07-02-durable-metrics-event-model.md`.

## Acceptance gates (current)

- Mandatory: test coverage 100%, docstring 100%, non-blocking request path,
  lightweight event queue, warning count 0, deprecated usage 0,
  and one-day delivery schedule with security verification evidence.
- Optional: DB pooler client path (when DB is introduced), PostgreSQL 17 support track.

Current release claim boundary:
- Mandatory gates are validated through committed evidence under `docs/qa/evidence/`.
- Optional DB pooler/PostgreSQL 17 tracks are documented only and not executed in this MVP release.

## Delivery schedule

- One-day customer delivery plan (including security verification):
  - `docs/plans/2026-02-20-24h-customer-delivery-plan.md`

## Documentation references

- `docs/architecture.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`
- `docs/business/2026-07-02-krw2b-valuation-kpi-model.md`
- `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md`
- `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`
- `docs/diligence/2026-07-02-buyer-diligence-index.md`
- `docs/security/2026-07-02-threat-model-data-handling.md`
- `docs/security/2026-07-02-signed-artifact-link-design.md`
- `docs/security/2026-07-02-auth-tenant-model.md`
- `docs/security/2026-07-02-license-allowlist-review.md`
- `docs/security/2026-07-02-license-policy.json`
- `docs/analytics/2026-07-02-durable-metrics-event-model.md`

## Transfer metadata

- Target owner repo: to be set during transfer.
- Tech stack: Java 21 / Spring Boot / Maven.
- Primary package: `com.clearfolio.viewer`.
- Current branch default assumption: `main`.
