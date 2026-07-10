# Clearfolio Viewer Threat Model and Data Handling Map

Date: 2026-07-02

This document closes the current buyer-diligence gap for a repository-scoped
threat model, data-flow map, and retention classification. It reflects the
current MVP runtime only. It does not claim production OIDC/JWT identity,
durable storage, antivirus scanning, or a hardened converter sandbox.

## Overview

Clearfolio Viewer is a Java 21 / Spring Boot WebFlux document-preview service.
Its current runtime lets a user upload a document, receive an asynchronous job
identifier, poll conversion status, open an HTML viewer shell, and fetch an
in-memory PDF preview artifact after conversion succeeds.
Authorized KPI snapshot exports may also be recorded as local append-only
metadata when `clearfolio.analytics-snapshot-ledger.path` is configured.

Primary runtime surfaces:

- `GET /`: buyer-demo document intake, session history, and runtime KPI shell.
- `POST /api/v1/convert/jobs`: multipart upload and async job submission.
- `GET /api/v1/convert/jobs/{jobId}`: job lifecycle status.
- `POST /api/v1/convert/jobs/{jobId}/retry`: operator retry for dead-lettered
  jobs using `X-Clearfolio-Operator-Id`.
- `GET /api/v1/viewer/{docId}` and `/api/v1/convert/viewer/{docId}`: JSON
  viewer bootstrap for succeeded jobs.
- `GET /viewer/{docId}`: PDF.js-backed HTML viewer shell.
- `GET /artifacts/{docId}.pdf`: converted PDF artifact with signed-token
  verification and single-range support.
- `GET /api/v1/analytics/kpi-snapshot`: read-only runtime KPI counters.
  Authorized calls can append snapshot metadata to the optional local KPI
  snapshot ledger.
- `GET /api/v1/analytics/kpi-snapshot-exports`: tenant-scoped exported KPI
  snapshot evidence without raw document content.
- `GET /healthz`: readiness probe.

The current security posture is MVP-grade and evidence-oriented. It has
bounded upload size, blocked HWP/HWPX defaults, policy override audit
fingerprints, warning-free compile gates, 100 percent production package
JaCoCo line/branch coverage, JavaDoc gates, Semgrep evidence, no-store artifact
responses, signed artifact tokens, runtime artifact-token revocation, artifact
read audit events, optional file-backed artifact-link ledger replay,
optional file-backed KPI snapshot ledger replay,
tenant-scoped JSON APIs, optional HMAC validation for gateway-signed tenant
headers, production-profile fail-closed startup when the tenant-claim signing
secret is missing, and viewer CSP headers. The largest production gaps are no
validated OIDC/JWT, no durable encrypted store, no centralized multi-replica
revocation/audit persistence for artifact tokens, no AV or file-type deep
inspection, and no isolated real converter runtime.

## Threat Model, Trust Boundaries, and Assumptions

### Assets and privileges

| Asset or privilege | Current holder | Why it matters |
| --- | --- | --- |
| Uploaded source bytes | Request memory during `POST /api/v1/convert/jobs` | Can contain confidential business documents. |
| Conversion job metadata | `InMemoryConversionJobRepository` | Includes file name, content type, hash, size, status, timing, and retry state. |
| Converted PDF bytes | `InMemoryArtifactStore` | Preview artifact is retrievable with a short-lived signed artifact token while the process is alive. |
| Artifact link ledger | `ArtifactLinkLedger` | Tracks issued artifact tokens, revocations, and read events; can optionally replay local file evidence across restart. |
| Approval token | Request header during blocked-format override | Must not be logged or persisted raw. |
| Operator retry authority | `X-Clearfolio-Operator-Id` header | Requeues dead-lettered jobs and affects audit trail. |
| Browser session history | User browser session | Contains local demo history and document/job labels. |
| Runtime KPI snapshot | Repository state projected by analytics API | Reveals operational volume, success rate, and latency. |
| KPI snapshot ledger | `KpiSnapshotLedger` | Tracks authorized KPI export metadata and can optionally replay local file evidence across restart. |
| Tenant-claim HMAC secret | Runtime configuration when enabled | Lets the gateway prove tenant headers were not forged by the client. |

### Trust boundaries

| Boundary | Untrusted side | Trusted side | Controls currently present |
| --- | --- | --- | --- |
| Public HTTP request to WebFlux controllers | Browser, API client, uploaded file, headers, path IDs, range header | Controller and service layer | UUID binding, multipart size cap, extension blocklist, range parsing, stable error responses, tenant permission checks, optional signed tenant headers. |
| Gateway tenant claims to service | Browser-facing gateway or host platform | `TenantAccessService` | Optional HMAC over tenant id, subject id, permissions, and issue time when `clearfolio.tenant-claims.hmac-secret` is configured. |
| Upload validation to background conversion | User-provided file and filename | Validation service, job repository, worker executor | HWP/HWPX block by default, max upload bytes, SHA-256 content hash dedupe, async queue. |
| Worker to artifact store | Conversion output bytes | In-memory PDF artifact store | Generated PDF is synthetic in current MVP, cloned on put/get. |
| Artifact ledger to local file | Issued link, revocation, and read-event metadata | Optional append-only file configured by `clearfolio.artifact-link-ledger.path` | Text fields are encoded and replayed locally; this is not a centralized production audit store. |
| KPI snapshot ledger to local file | Tenant id, subject id, export time, aggregate counts, success rate, p95 preview latency | Optional append-only file configured by `clearfolio.analytics-snapshot-ledger.path` | Text fields are encoded and replayed locally; this is not a durable analytics event stream. |
| KPI snapshot evidence lookup | Request tenant id and analytics permission | `AnalyticsController` and `KpiSnapshotLedger` | Returns only the caller tenant's exported snapshot metadata and omits tenant id from the response body. |
| Viewer HTML to browser | Static JS/CSS, PDF.js iframe, signed artifact URL | User browser | CSP on `/viewer`, artifact token verification, `no-store`, `nosniff`, `no-referrer`, same-origin defaults. |
| Operator retry | Operator-supplied identifier | Dead-letter retry transition | Non-blank operator header required; retry only for failed dead-lettered jobs. |
| Analytics API | Any caller with network access | In-memory job repository | Read-only projection, no raw upload bytes, no token output. |

### Assumptions

- The current service is an MVP running behind a trusted network or demo
  environment. It is not internet-hardened as a standalone SaaS service.
- Tenant headers are unsigned in the default local buyer-demo profile. If
  `clearfolio.tenant-claims.hmac-secret` is configured, the service requires
  gateway HMAC signatures and rejects missing, stale, future, or invalid
  signed claims. The `production` Spring profile also fails startup unless that
  secret is configured.
- `docId` is no longer sufficient to read artifacts; artifact reads require a
  signed token bound to document, tenant, expiry, scope, and checksum. Issued
  tokens are recorded in a runtime ledger and can be revoked by `tokenId`. The
  ledger can optionally persist to a local append-only file for restart replay,
  but durable external revocation state is not implemented yet.
- Source document bytes are not durably stored by the current application after
  submission. Converted PDF bytes and metadata live in memory until process
  restart.
- HWP/HWPX override headers are controlled by an operator process outside the
  current codebase. The code audits a token fingerprint but does not validate
  that token against an external policy store.
- The current converter is a PDFBox-based preview generator. A future real
  Office/HWP converter must be treated as a separate high-risk sandbox boundary.
- Buyer-demo browser history is local to the browser session and is not server
  evidence.

## Attack Surface, Mitigations, and Attacker Stories

### Attacker-controlled inputs

- Multipart file bytes, file name, content type, and file extension.
- Policy override headers: `X-Clearfolio-Policy-Override`,
  `X-Clearfolio-Approval-Token`, and `X-Clearfolio-Approver-Id`.
- Operator retry header `X-Clearfolio-Operator-Id`.
- Tenant signing headers `X-Clearfolio-Claims-Issued-At` and
  `X-Clearfolio-Claims-Signature`.
- `jobId` and `docId` path variables.
- `artifactToken` query parameter or bearer token for artifact reads.
- HTTP `Range` header for artifact reads.
- Optional `X-Trace-Id` header reflected in API error payloads.
- Browser execution environment for the root shell and viewer shell.

### Existing mitigations

- `ConversionController` joins multipart content with
  `spring.codec.max-in-memory-size` derived from `conversion.max-upload-size-bytes`.
- `DefaultDocumentValidationService` rejects missing files, missing extensions,
  blocked extensions, and files above `maxUploadSizeBytes`.
- HWP/HWPX override requires explicit true/false override syntax, a non-blank
  token, and a non-blank approver id; raw tokens are not logged.
- Override audit logs sanitize control characters and log only an 8-byte
  SHA-256 token fingerprint.
- `ConversionJob` strips NUL characters from persisted strings.
- `TenantAccessService` can require gateway-signed tenant claims with HMAC,
  300-second default skew, and constant-time signature comparison when a shared
  secret is configured.
- `DefaultDocumentConversionService` hashes content with SHA-256 and dedupes by
  content hash.
- `DefaultConversionWorker` uses a bounded executor, retry backoff, and
  dead-letter terminal state.
- `ArtifactController` serves artifacts only for `SUCCEEDED` jobs with a valid
  artifact token, supports one range, rejects invalid or unsatisfiable ranges,
  and sets `no-store` plus `X-Content-Type-Options: nosniff`.
- `ViewerSecurityHeadersWebFilter` applies `Cache-Control: no-store`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and a CSP
  with `frame-ancestors`, `object-src 'none'`, same-origin scripts/styles, and
  PDF.js-compatible worker/frame rules for `/viewer`.
- `ApiExceptionHandler` returns stable error shapes and logs unexpected errors
  with sanitized path and trace id.

### Realistic attacker stories

- An unauthenticated client uploads oversized or many files to exhaust request
  memory, worker threads, or in-memory artifact storage. Current caps and queue
  limits reduce but do not eliminate this risk because there is no auth,
  request throttling, or process-level memory quota documented.
- A client uploads a malicious file to exploit a future real converter. Current
  MVP synthetic PDF generation keeps this low today, but a production converter
  must run in an isolated sandbox with file-type verification, timeout, and AV
  or malware-scanning evidence.
- A client guesses or obtains a `docId` and fetches a preview artifact. UUID
  entropy helps against guessing, and artifact reads now require a signed token;
  the optional file-backed ledger can restore local revocation and read-event
  evidence after a restart, but production still needs durable token metadata,
  revocation, and read-audit persistence across replicas.
- A public client forges `X-Clearfolio-*` tenant headers to impersonate another
  tenant. Tenant ownership checks limit cross-tenant object access, and the
  optional HMAC mode rejects forged headers when enabled; unsigned local demo
  mode must not be exposed as a production internet boundary. The `production`
  profile now fails closed if the HMAC secret is absent.
- A caller abuses policy override headers to push blocked HWP/HWPX files through
  the system. Current code requires header presence and creates audit evidence,
  but token validation and policy-owner approval live outside the repository.
- A crafted filename, trace id, or operator id attempts log injection. Current
  log sanitization covers override and unexpected-error logs, while API payloads
  can still echo client-controlled values for diagnostics.
- A browser tries to embed or script the viewer in an unsafe frame. Current CSP
  defaults to same-origin `frame-ancestors`, but production embedding needs an
  explicit allowlist by deployment domain.

### Out-of-scope or lower-realism stories

- SQL injection is not currently applicable because the repository has no SQL
  persistence layer.
- Stored XSS through converted documents is lower risk in the current synthetic
  PDF generator. It becomes high risk when real document rendering, OCR, or
  HTML conversion is introduced.

## Data Handling Map

| Step | Data entering step | Code owner | Data stored after step | Current retention | Buyer risk |
| --- | --- | --- | --- | --- | --- |
| Upload request | Source bytes, filename, content type, override headers | `ConversionController` | In-memory multipart wrapper | Request scope | Large payload and malicious file risk. |
| Validation | File metadata, size, extension, override headers | `DefaultDocumentValidationService` | No source bytes persisted | Request scope | Policy-token validation is external. |
| Tenant claim validation | Tenant id, subject id, permissions, issue time, signature | `TenantAccessService` | No request claims persisted by the auth service | Request scope | Unsigned demo mode must stay local; production secrets need managed configuration. |
| Job creation | Filename, content type, hash, size, tenant id, subject id | `DefaultDocumentConversionService` | `ConversionJob` metadata | Process lifetime | No durable retention policy. |
| Queue and retry | Job id, status, attempt count, retry time | `DefaultConversionWorker` | Job lifecycle fields | Process lifetime | Worker saturation and retry audit gaps. |
| Artifact generation | Job metadata | `PdfBoxArtifactGenerator` | Synthetic PDF bytes | Process lifetime | Real converter sandbox not present. |
| Artifact serving | `docId`, `artifactToken`, optional range | `ArtifactController` | Runtime ledger and read audit event; optional local append-only ledger file | Process lifetime by default; local-file replay when configured | No centralized multi-replica revocation or read-audit persistence. |
| Viewer shell | `docId`, status, artifact path | `ViewerUiController`, `viewer.js` | Browser-rendered state | Browser tab lifetime | Embedding domain matrix not finalized. |
| Demo shell | User file picker state, session jobs, KPI snapshot | `demo.js` | Browser session history | Browser session | Session history is not auditable server data. |
| KPI snapshot | Tenant-filtered job metadata aggregate | `AnalyticsController` | Optional snapshot ledger record | Response scope by default; local-file replay when configured | No durable lifecycle metrics event store. |
| KPI snapshot export lookup | Tenant id and analytics permission | `AnalyticsController` | No new storage; reads `KpiSnapshotLedger` | Response scope | Exposes commercial metadata; production needs durable audit and retention policy. |

## Retention and Classification

| Data class | Classification | Current storage | Current retention | Production requirement |
| --- | --- | --- | --- | --- |
| Source document bytes | Confidential customer content | Request memory only | Request processing window | Encrypted object quarantine, malware scan, deletion SLA. |
| Converted PDF artifact | Confidential customer content | JVM memory | Until process restart | Encrypted object store, durable token metadata, persisted revocation, TTL, tenant ACL. |
| Artifact link and read-event metadata | Customer access metadata | JVM memory by default; optional local append-only ledger file | Process lifetime by default; local-file retention follows host file policy when configured | Durable audit/revocation table, encryption at rest, retention SLA, and cross-replica lookup. |
| File name and content type | Customer metadata | In-memory job repository | Until process restart | Tenant-scoped metadata table with retention policy. |
| Content hash | Derived document identifier | In-memory job repository | Until process restart | Treat as sensitive metadata; avoid cross-tenant dedupe. |
| Job status and timings | Operational metadata | In-memory job repository | Until process restart | Durable event table for audit and KPI reporting. |
| KPI snapshot export metadata | Commercial and operational aggregate metadata | JVM memory by default; optional local append-only ledger file | Process lifetime by default; local-file retention follows host file policy when configured | Durable analytics event table and tenant-scoped daily projections. |
| Approval token | Secret-like policy credential | Not persisted raw | Request only | External policy validation and secret redaction evidence. |
| Approval token fingerprint | Audit metadata | Log line only | Log retention policy | Central audit log with owner and review workflow. |
| Tenant-claim HMAC secret | Secret configuration | Runtime config only | Deployment secret lifetime | Managed secret storage and rotation before production. |
| Operator id | Operator audit metadata | Job status message and logs | Until process restart/log retention | Authenticated operator identity and immutable audit log. |
| Browser session history | Local demo metadata | Browser session | Browser session | Do not treat as buyer evidence; seed server demo data instead. |

## Severity Calibration

### Critical

- Unauthorized cross-tenant artifact access after tenant support is introduced.
- Remote code execution through a real converter, parser, PDF renderer, or file
  processing dependency.
- Raw approval tokens, source documents, or converted artifacts written to
  logs or committed evidence bundles.

### High

- Artifact token leakage can expose a preview until token expiry or runtime
  revocation; optional local file replay helps single-process restart recovery,
  but production deployments still need centralized durable revocation state
  across replicas.
- Forged tenant headers remain high risk if unsigned local demo mode is exposed
  outside a trusted local/demo network.
- Malicious uploads can exhaust memory, worker capacity, or artifact storage
  without rate limiting.
- Policy override accepts blocked formats without external token validation or
  immutable policy-owner audit evidence.

### Medium

- CSP `frame-ancestors` is not configured to the buyer's exact production
  embedding domains.
- Runtime KPI endpoint exposes internal operational volume in a public
  deployment. The optional snapshot ledger also records export metadata and must
  be handled as tenant-scoped commercial evidence.
- Trace ids, filenames, or operator ids appear in API payloads or logs without
  consistent downstream redaction.

### Low

- Health endpoint reveals service availability.
- Demo shell browser history persists locally for the session.
- In-memory storage loses metadata on restart in the MVP demo environment.

## Buyer Evidence Closure

This document moves the following diligence items forward:

- Threat model and policy-owner matrix: threat model is now present; the policy
  owner matrix remains a separate governance artifact.
- Data handling map and retention policy: current-state map is present; durable
  production retention policy remains required.
- Security evidence: Semgrep remains the current SAST artifact. CycloneDX SBOM
  evidence is now present, but license allowlist/legal review remains open.

Next implementation slices, in order:

1. Complete license policy and allowlist review for the generated SBOM.
2. Move buyer deployments to configured gateway-signed tenant headers, then
   replace the scaffold with validated gateway/OIDC claims.
3. Promote the optional local artifact-link ledger into durable artifact
   metadata and centralized token revocation plus read-audit persistence.
4. Promote the optional local KPI snapshot ledger into durable analytics events,
   job lifecycle events, and commercial KPI projections.
5. Add deployment security profile with production `frame-ancestors` matrix.
