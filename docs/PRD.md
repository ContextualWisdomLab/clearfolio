# Clearfolio Product Requirements Document

Status: Canonical product requirements spine
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

## Product purpose

Clearfolio is an independently deployable secure document-conversion and viewing service that can also compose as a bounded service inside naruon and other ContextualWisdomLab systems. It accepts untrusted document uploads, performs bounded asynchronous processing, exposes state and controlled artifacts, and preserves tenant, evidence, and release boundaries. Browsers, LLMs, and downstream hosts do not become authority for conversion correctness, authorization, or release acceptance.

## Maturity vocabulary

Requirements use explicit implementation labels:

- `IMPLEMENTED_ON_MAIN`: evidenced on protected default branch.
- `ACTIVE_PR`: implemented on an open PR only; not shipped.
- `PARTIAL`: useful subset exists but the requirement is incomplete.
- `ACCEPTED_ARCHITECTURE`: accepted target boundary without protected-main implementation evidence.
- `PLANNED`: backlog work.
- `OUT_OF_SCOPE`: intentionally excluded.

## Primary users and buyer outcomes

### Document user

A document user needs to submit a document without blocking on conversion, understand progress and failures, open accessible converted output, and retrieve only artifacts they are authorized to read.

### Tenant operator

A tenant operator needs least-privilege administrative listing/retry/delete workflows, concealed cross-tenant resources, restart-safe recovery evidence, privacy-safe audit trails, and accurate liveness/readiness signals.

### Platform integrator

A platform integrator needs stable versioned APIs and explicit service-ownership boundaries so Clearfolio works alone or behind naruon/gateway/orchestration infrastructure without hidden database coupling.

### Security, procurement, and acquisition reviewer

A reviewer needs exact evidence for tenant isolation, active-content and external-resource boundaries, dependency and supply-chain integrity, document fidelity, accessibility, recovery, observability, and release provenance.

## Core product journeys

### Submit and process

`IMPLEMENTED_ON_MAIN`: `POST /api/v1/convert/jobs` performs bounded upload intake and delegates conversion work away from the WebFlux request path. Jobs expose status, retries, dead-letter state, content identity, and current process-local recovery behavior.

`PLANNED`: durable distributed job intake, cancellation, backpressure, persistent queue semantics, and database-backed recovery.

### View and retrieve output

`IMPLEMENTED_ON_MAIN`: PDF passthrough, PDF.js viewer bootstrap, signed viewer artifact links, token ledger/revocation/read audit for the canonical artifact route, and tenant-scoped JSON APIs exist.

`PARTIAL`: direct conversion-job download is under security remediation to align with the signed artifact-delivery contract.

`PARTIAL`: protected main does not provide production-fidelity Office conversion. `PdfBoxArtifactGenerator` generates a placeholder one-page PDF for non-PDF sources; that behavior is not a supported-format fidelity claim.

### Recover from failure

`IMPLEMENTED_ON_MAIN`: bounded worker retry/dead-letter behavior, operator retry, process-local startup recovery, and file-backed artifact options exist.

`ACTIVE_PR`: #268 adds stronger durable deletion receipts, generation fencing, retry fairness, crash-tail validation, and restart recovery.

`ACTIVE_PR`: #295 separates process liveness from traffic readiness.

### Enforce tenant and privacy boundaries

`IMPLEMENTED_ON_MAIN`: tenant contexts, permissions, same-tenant checks, cross-tenant concealment, signed viewer artifact links, revocation and audit evidence exist in the current scaffold.

`ACTIVE_PR`: #270 and #268 strengthen signed tenant claims, dedicated artifact/admin least privilege, purpose-separated cryptographic keys, domain-separated HMAC audit pseudonyms, immutable job identity, and durable mutation boundaries.

`PLANNED`: production IdP/OIDC issuer/audience/expiry/revocation/role mapping, enterprise tenant lifecycle, data-retention policy, and distributed policy enforcement.

### Operate an accessible viewer

`IMPLEMENTED_ON_MAIN`: a responsive viewer shell and PDF.js rendering path exist.

`ACTIVE_PR`: #264 adds contextual accessible names and nested/reentrant busy-state handling with exact DOM/ARIA/disabled restoration.

`PLANNED`: broader keyboard, screen-reader, print/export and assistive-technology acceptance evidence.

## Functional requirements

| ID | Requirement | Maturity |
| --- | --- | --- |
| FR-01 | Accept bounded document uploads and return an asynchronous job contract without inline conversion | `IMPLEMENTED_ON_MAIN` |
| FR-02 | Expose tenant-scoped job status and stable lifecycle metadata | `IMPLEMENTED_ON_MAIN` |
| FR-03 | Expose state-gated viewer bootstrap and controlled artifact delivery | `IMPLEMENTED_ON_MAIN` / direct-download hardening `ACTIVE_PR` |
| FR-04 | Preserve content identity and idempotent dedupe behavior at the current repository boundary | `IMPLEMENTED_ON_MAIN` |
| FR-05 | Fail closed for blocked formats, malformed input and bounded upload limits | `IMPLEMENTED_ON_MAIN` |
| FR-06 | Require least-privilege authorization before tenant repository/artifact/admin access | `IMPLEMENTED_ON_MAIN`, strengthened in `ACTIVE_PR` |
| FR-07 | Preserve immutable lifecycle identity and restart-safe artifact deletion evidence | `ACTIVE_PR` #268 |
| FR-08 | Separate liveness from traffic readiness | `ACTIVE_PR` #295 |
| FR-09 | Make repeated asynchronous viewer actions contextually named and nested-safe | `ACTIVE_PR` #264 |
| FR-10 | Convert each claimed Office format using deterministic real-fixture fidelity acceptance instead of placeholder output | `PLANNED`, issue #5 |
| FR-11 | Provide durable async jobs with cancellation, backpressure, restart recovery and distributed idempotency | `PLANNED` |
| FR-12 | Publish OpenTelemetry traces and low-cardinality privacy-safe metrics | `PLANNED` |
| FR-13 | Maintain versioned naruon/MSA contracts without cross-service application-database access | `ACCEPTED_ARCHITECTURE` |
| FR-14 | Produce SBOM, attribution, reproducibility and release-provenance evidence tied to exact source | `PARTIAL`, strengthened in #270 |
| FR-15 | Support truthful tenant-safe deletion/download UX on the durable lifecycle substrate without duplicate cleanup or signed-link bypass | `PLANNED` after #270/#268/#264 integration, issue #263 |

## Non-functional requirements

- **Fail closed:** malformed, unauthorized, cross-tenant, stale-generation, unsupported, oversized and unverifiable requests fail with controlled semantics.
- **Exact evidence:** predecessor-head, stale-base, queued, pending, skipped-required, cancelled, absent, synthetic-only, model-only and status-only evidence is not exact-head release evidence.
- **Coverage:** owned production statement and branch coverage is exactly 100%; public production APIs have beginner-readable Javadocs/docstrings.
- **Naming:** owned database/domain persistence objects use at least two descriptive `snake_case` words unless an external protocol requires another style.
- **Privacy:** pseudonymized identifiers remain personal data; use purpose-bound access, minimization, encryption, retention controls and auditable privileged access rather than blanket masking.
- **Cryptography:** cryptographic purposes use separated keys and versioned/domain-separated constructions where applicable.
- **Deterministic authority:** conversion, authorization, security validation, fidelity measurement and release decisions remain deterministic; LLM output cannot override them.
- **Workflow security:** use least privilege and immutable workflow/action/source pinning where practical.
- **Compatibility:** preserve standalone deployment and modular MSA integration through explicit APIs and replaceable adapters.

## Document fidelity contract

A format may be advertised as supported only when the current implementation has all of the following:

1. authorized or redistributable realistic source fixtures;
2. expected structural, visual, extraction, accessibility, security and failure outcomes;
3. deterministic source/artifact identity where meaningful;
4. documented tolerated differences and unsupported constructs;
5. active-content, macro, external-resource, malformed-container and resource-boundary tests;
6. exact-head regression evidence and release acceptance.

Until those conditions are met, non-PDF placeholder generation is development/demo behavior and must not be described as Office conversion fidelity. `docs/FIDELITY_ACCEPTANCE.md` is the detailed qualification authority and issue #5 is the current executable product gap.

## Automation and development contract

`ACTIVE_PR`: #271 defines a repository-local OpenCode product-development loop. It uses `NVIDIA_NIM_API_KEY` only through GitHub Secrets, never `COPILOT_GITHUB_TOKEN` as a development-model credential, and separates credential-bearing model proposal from credential-free verification/publication. Its decision loop is RCA → materially distinct remedies → real-world feasibility → same-run action → proof. Review/check latency blocks only the affected action; path-disjoint safe work continues.

`ACCEPTED_ARCHITECTURE`: pull-request maintenance and merge governance belong to the central ContextualWisdomLab `.github` control plane. Clearfolio must not duplicate that privileged control-plane implementation.

Autonomous development may propose bounded Draft PRs but may not self-approve, weaken tests/protection, merge, release or deploy its own work.

## Product gaps prioritized by acquisition impact

1. Real deterministic Office conversion plus realistic fidelity benchmark suite (issue #5).
2. Durable async job state, idempotency, backpressure, cancellation and distributed recovery.
3. Production identity/tenant lifecycle and least-privilege enterprise authorization.
4. Privacy-safe OpenTelemetry traces/metrics and operator SLO evidence.
5. Object-store/database durability with rollback/recovery and tenant isolation.
6. Accessible, truthful tenant-safe download/deletion UX after the active lifecycle/security substrate integrates (issue #263).
7. Accessibility/print/export acceptance across other supported viewer workflows.
8. Versioned naruon integration contracts and compatibility tests.
9. Reproducible packaging, SBOM/provenance, signed release and operational acceptance.

## Release acceptance

A release may be published only from an exact integrated protected head that passes CI, security, exact coverage/docstrings, realistic document-fidelity acceptance, accessibility, packaging, SBOM/provenance, reproducibility, API/schema compatibility, migration/rollback/recovery where applicable, qualifying independent review, and protected-main operational acceptance. Version bumps and release notes are consequences of this evidence, not substitutes for it.

Detailed release authorities are `docs/FIDELITY_ACCEPTANCE.md`, `docs/MIGRATION_ROLLBACK.md`, and `docs/RELEASE_ACCEPTANCE.md`. These documents define evidence/compatibility/recovery rules; they do not turn an `ACTIVE_PR` implementation into `IMPLEMENTED_ON_MAIN` behavior.

## Non-goals

- LLM-based decisions for deterministic conversion or access control.
- Hidden cross-service database access to naruon or other CWL services.
- Claiming Office fidelity from placeholder output.
- Self-approval or protection bypass by autonomous development.
- Claiming certifications, SLOs, durability, distributed transactions, or enterprise IdP behavior without measured or integrated evidence.
