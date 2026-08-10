# Clearfolio Technical Requirements Document

Status: Canonical technical requirements spine
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

## 1. Technical objective

Clearfolio provides a bounded Spring WebFlux service for untrusted document intake, asynchronous conversion lifecycle management, tenant-scoped viewer/artifact access, and operational evidence. The technical design must preserve standalone operation while exposing replaceable interfaces for naruon and other CWL hosts.

This document separates protected-main implementation from active pull requests and target architecture. An `ACTIVE_PR` statement is never release evidence.

## 2. Runtime boundaries

### HTTP/control plane

`IMPLEMENTED_ON_MAIN`:

- `ConversionController` accepts uploads, exposes status/retry/viewer/direct-download APIs, and delegates heavy work away from request processing.
- `ArtifactController` owns signed artifact-link creation, revocation, read-audit access, signed PDF delivery, and single-range behavior for the canonical artifact route.
- `ViewerUiController` serves the HTML/PDF.js viewer shell.
- `ApiExceptionHandler` provides controlled API failures.

### Conversion plane

`IMPLEMENTED_ON_MAIN`:

- `DocumentConversionService` / `DefaultDocumentConversionService` orchestrates validation, content identity, dedupe, state-store interaction, artifact seeding and worker enqueue.
- `DefaultConversionWorker` runs bounded background work with retry/dead-letter behavior and startup recovery against available repository state.
- PDF uploads with validated PDF magic can be passed through to the artifact store.

`PARTIAL`:

- `PdfBoxArtifactGenerator` produces placeholder one-page PDFs for non-PDF sources. It is not an Office-fidelity implementation.

`PLANNED`:

- production Office conversion adapter(s) with no unreviewed active-content execution or external network dependency;
- fixture-based fidelity evidence for each claimed source format;
- explicit unsupported/degraded behavior.

The detailed transformed-format qualification authority is `docs/FIDELITY_ACCEPTANCE.md`; issue #5 is the active product gap. A successful converter process exit, accepted extension, or generated placeholder cannot independently authorize a support claim.

### Persistence/evidence plane

`IMPLEMENTED_ON_MAIN`:

- `InMemoryConversionJobRepository` is the default job repository.
- `FileSystemArtifactStore` can persist artifact bytes and metadata locally; in-memory mode remains available for tests/development.
- selected evidence uses append-only/file-backed ledgers such as artifact-link and analytics snapshot ledgers when configured.

`ACTIVE_PR` #268:

- durable artifact deletion receipts, generation fencing, append-only recovery evidence and fairness/restart hardening.

`PLANNED`:

- SQL-backed durable conversion jobs/state transitions;
- distributed lease/idempotency/cancellation/backpressure contracts;
- remote object-store atomicity and lifecycle coordination.

Current/future persistence compatibility, rollback and restore semantics are governed by `docs/MIGRATION_ROLLBACK.md`; the conceptual ERD in `docs/DATA_MODEL.md` never implies that planned SQL entities already exist.

## 3. Security and authorization boundaries

### Tenant authority

`IMPLEMENTED_ON_MAIN`: `TenantContext`, `TenantAccessService`, and `TenantPermissions` provide tenant/subject/permission boundaries and same-tenant concealment semantics. Signed gateway claims can be configured; unsigned demo scaffolding is not a production internet trust boundary.

`ACTIVE_PR` #270/#268 strengthens:

- signed tenant claim requirements;
- dedicated least-privilege admin/artifact permissions;
- purpose-separated signing/audit keys;
- domain-separated HMAC audit pseudonyms;
- cross-tenant concealment before artifact/repository mutation;
- immutable lifecycle identity/generation fences.

### Runtime credential authority

`PRESENT-DEVIATION-ON-MAIN`: the repository-level `AGENTS.md` records that the artifact-token HMAC secret and tenant-claim HMAC secret are currently transported directly into Spring runtime configuration through environment-backed placeholders. This is an acknowledged governance deviation, not the target credential architecture.

`ACCEPTED_ARCHITECTURE`:

- runtime code reads secrets from a credential/KV registry rather than treating raw process environment as the runtime source of truth;
- deployment/CI may use an environment value only as bounded bootstrap transport into that registry;
- the credential record is purpose-scoped, tenant/deployment scoped where applicable, auditable, rotation-capable, and never exposed through logs, metrics, public errors, health payloads, or document-derived data;
- artifact-token signing, tenant-claim signing, audit pseudonymization, model credentials, and repository automation credentials remain separate purposes and authorities;
- standalone mode may use a replaceable local credential-store adapter, but production must not silently fall back to unsigned/demo behavior when the configured registry is unavailable.

Migrating this deviation is a product/security task. Documentation must not claim that the current protected-main secret source is already KV-backed.

### Artifact authority

`IMPLEMENTED_ON_MAIN`: `ArtifactLinkService` issues HMAC-bound, tenant/document/checksum/scoped tokens with expiry, ledger presence, revocation, and read-audit evidence. `ArtifactController#getPdf` validates signed tokens and single-range requests before serving bytes.

`ACTIVE_PR` #270 remediation: direct `ConversionController` download must use the same signed-delivery semantics. Dedicated `artifact:read` permission is necessary but not sufficient to bypass signed token/revocation/audit requirements.

### Privacy-safe audit

Raw secrets and uncontrolled exception-selected details must not become durable/shared audit evidence. Audit correlation identifiers must be purpose-bound. Pseudonymization reduces direct identifiability but does not remove personal-data status.

## 4. API requirements

### Asynchronous conversion

- `POST /api/v1/convert/jobs`: bounded multipart intake; return accepted job contract; no conversion completion wait.
- `GET /api/v1/convert/jobs/{jobId}`: tenant-scoped lifecycle snapshot.
- `POST /api/v1/convert/jobs/{jobId}/retry`: controlled dead-letter retry.
- `DELETE /api/v1/convert/jobs/{jobId}` and admin mutation surfaces must enforce tenant/generation-safe semantics appropriate to the integrated branch.

### Viewer and artifacts

- `/viewer/{docId}`: HTML viewer entry.
- `/api/v1/viewer/{docId}` and alias: protected bootstrap data and signed artifact-link metadata for succeeded jobs.
- `/api/v1/viewer/{docId}/artifact-links`: tenant-authorized signed artifact-link issuance.
- `/artifacts/{docId}.pdf`: signed token + revocation + checksum binding + single-range + read audit.
- `/api/v1/convert/jobs/{jobId}/download`: direct-download convenience endpoint; active remediation aligns it to the same signed artifact-delivery authority while retaining dedicated tenant permission and attachment semantics.

All new public API/schema changes require version/compatibility analysis and regression coverage. `docs/API_CONTRACT.md` is the version/authority index; implementation-specific examples must not silently redefine its tenant, token, lifecycle, or error semantics.

## 5. Job lifecycle requirements

Protected-main lifecycle uses `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, and `FAILED`, with retry exhaustion represented through dead-letter evidence rather than a separate public terminal enum.

Required invariants:

1. one immutable job identity must not be rebound to a different tenant/content generation;
2. only valid transitions may be persisted;
3. worker retry is bounded and observable;
4. stale processing can be recovered only under an explicit lease/age contract;
5. duplicate submissions do not create inconsistent content ownership;
6. delete/retry operations must not cross tenant or generation fences;
7. future distributed storage must preserve idempotency and recovery under worker crash/restart.

`ACTIVE_PR` #268 is the lower-layer implementation owner for immutable generation, deletion receipts, cleanup recovery and related administrative lifecycle hardening. Umbrella issue #263 owns the remaining integrated user-facing deletion/download API and accessible UX after #270/#268/#264 integrate. New work must reuse that substrate rather than duplicate it on another branch.

## 6. Conversion and fidelity requirements

### Format classification

- `passthrough`: source bytes are the claimed view artifact after deterministic validation (for example, accepted PDF passthrough).
- `transformed`: actual conversion is executed and validated against a fidelity profile.
- `degraded`: controlled lower-fidelity output with explicit user/operator warning and release-approved scope.
- `unsupported`: controlled failure; no placeholder may masquerade as successful conversion.
- `development_placeholder`: test/demo-only behavior not eligible for supported-format claims.

### Fidelity evidence

Each transformed format requires:

- authorized or redistributable realistic fixtures covering text, fonts, tables, images, pagination and representative edge cases;
- expected extraction/render structure and artifact properties;
- comparison rules that tolerate only documented nondeterminism;
- macro/active-content/external-resource/malformed-container tests;
- size/time/resource bounds and cleanup tests;
- accessibility/print/export behavior where user-visible;
- exact-head benchmark evidence retained separately from timeless requirements.

`docs/FIDELITY_ACCEPTANCE.md` defines the detailed support taxonomy, deterministic/provenance requirements and release evidence for issue #5.

## 7. Availability and recovery

`IMPLEMENTED_ON_MAIN`: health endpoint and process-local startup recovery exist.

`ACTIVE_PR` #295: `/healthz` becomes process liveness and `/readyz` traffic readiness, with controlled low-information responses, `no-store`, and deterministic startup/failure/recovery tests.

Future readiness contributors must be instance-local routing conditions. Shared-service health must not turn liveness into a dependency restart cascade.

Durable-format, artifact-store, converter/runtime, API/token and future SQL migration/rollback/recovery are governed by `docs/MIGRATION_ROLLBACK.md`. Recovery is complete only after restored state passes the tenant/security and representative buyer-flow checks appropriate to the release.

## 8. Observability

`PARTIAL`: structured API errors and selected audit/analytics evidence exist.

`PLANNED`:

- OpenTelemetry traces for intake, validation, enqueue, conversion, artifact publication and recovery;
- low-cardinality metrics for queue depth/lag, conversion outcome, retry/dead-letter, readiness and artifact cleanup;
- privacy-safe correlation that does not publish tenant, filename, raw token, document digest or uncontrolled exception values as metric labels;
- measured SLO evidence before any availability claim.

## 9. Standalone and MSA interoperability

Clearfolio owns document conversion/viewing behavior, local authorization enforcement, artifact-delivery rules, its own state interfaces and evidence contracts. A host such as naruon may own user-facing orchestration, identity exchange, transport composition and deployment policy, but must not reach directly into Clearfolio application persistence.

Integration requirements:

- stable versioned HTTP/schema contracts;
- replaceable repository/artifact/conversion/credential adapters;
- explicit tenant/actor authority handoff;
- no hidden dependency on a central CWL repository at runtime;
- central `.github` reusable automation remains control-plane tooling, not a product runtime dependency.

## 10. Automation architecture

### PR maintenance

`ACCEPTED_ARCHITECTURE`: central `ContextualWisdomLab/.github` owns privileged review → repair → exact-head revalidation → protected merge automation. Leaf repositories should use thin callers/contracts rather than copy privileged implementation.

### Product development

`ACTIVE_PR` #271 proposes a Clearfolio-local, bounded, path-disjoint OpenCode development workflow. It uses `NVIDIA_NIM_API_KEY` only at the model step, performs credential-free verification, publishes only a Draft through short-lived scoped authority, and never approves/merges/releases/deploys its own proposal.

Decision contract: exact evidence → RCA → distinct remedies → empirical feasibility → test-first action → exact proof → next executable queue item. A blocker report or one completed artifact is not a successful run while safe work remains.

## 11. Quality gates

- Java 21 and repository-defined Maven toolchain.
- `mvn -B --no-transfer-progress verify` is the canonical full Maven acceptance command.
- Surefire evidence: positive executed count; zero skip/failure/error; strict bounded safe XML parsing.
- optional Failsafe evidence follows the same rule when present.
- JaCoCo: zero missed owned production lines and branches.
- warning/deprecation failures remain enabled.
- warning-free public Javadocs for production public API.
- JS viewer logic touched by production flows is covered by its repository coverage gate.
- security/SAST/fuzz and other required exact-head checks must pass according to live repository policy.
- queued/pending/skipped-required/cancelled/absent/stale/predecessor/synthetic-only evidence is never promoted to success.

## 12. Supply-chain and release requirements

- dependencies and actions are explicitly governed and immutably pinned where practical;
- third-party attribution and CycloneDX SBOM are reproducible from source when claimed;
- release provenance must bind source revision, dependency lock/BOM state, build workflow and produced artifact;
- release only after realistic document-fidelity, security, coverage, accessibility, compatibility, recovery, independent-review and protected-main operational evidence is complete.

`docs/RELEASE_ACCEPTANCE.md` is the detailed integrated gate. It requires one exact protected source/artifact identity and keeps source-head, live-base, workflow, review, security, fidelity/recovery and SBOM/provenance evidence separate. A successful PR check, local build, predecessor head or model verdict cannot substitute for integrated release acceptance.

## 13. Technical non-goals

- arbitrary JavaScript/macro execution from uploaded documents;
- uncontrolled external fetches during deterministic conversion;
- LLM-based authorization, format validation or fidelity pass/fail;
- raw process environment as the production runtime credential source of truth;
- assuming in-memory state is durable distributed persistence;
- treating a PR-body SHA or status-only bot result as current integration evidence;
- duplicating central control-plane write authority inside the product repository;
- duplicating #268 lifecycle/deletion substrate to bypass its dependency/review gate.
