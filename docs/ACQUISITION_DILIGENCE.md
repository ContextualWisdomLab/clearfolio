# Clearfolio Acquisition Diligence

Status: Canonical acquisition-readiness index
Assessment date: 2026-08-10
Protected-main baseline assessed: `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

This document is the current buyer/acquirer diligence authority for Clearfolio. It separates protected-main evidence, `ACTIVE_PR` evidence, historical evidence, external evidence, and unresolved acquisition risk. It does **not** convert a green pull request, demo artifact, valuation narrative, model review, or historical snapshot into production or acquisition readiness.

The dated `docs/diligence/2026-07-02-buyer-diligence-index.md`, `docs/diligence/2026-07-03-buyer-readiness-scorecard.md`, buyer-demo screenshots, FigJam/Slides handoffs, and `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/` remain **historical evidence**. They explain what was proven at that time but do not override current protected main, this document, live pull requests, current checks/reviews, or the canonical architecture graph.

## Evidence vocabulary

- `PROTECTED_MAIN`: observable in the current protected default branch.
- `ACTIVE_PR`: implemented or documented only in an open PR; not shipped.
- `PARTIAL`: material evidence exists but an acquirer would still inherit an unresolved risk or missing control.
- `PLANNED`: accepted backlog/architecture without complete implementation evidence.
- `EXTERNAL`: requires evidence outside this repository, such as legal ownership records, customer contracts, certification reports, or infrastructure-specific attestations.
- `HISTORICAL`: dated evidence that must not be reused as current proof without revalidation.

Every acquisition claim must identify its evidence class. `ACTIVE_PR`, `HISTORICAL`, model output, status checks, and PR-body prose are never silently promoted to `PROTECTED_MAIN`.

## Current acquisition verdict

Clearfolio has a substantial technical diligence base but is **not yet acquisition-ready as a finished commercial document-conversion product**. The repository can explain its current architecture, security boundaries, tenant/artifact authority, release controls, historical quality evidence, and major product gaps. However, an acquirer would still inherit material product and operational completion risk:

- non-PDF conversion on protected main still includes a development/demo placeholder rather than production Office fidelity;
- production Office qualification remains issue #5 with provider-neutral contract/security work in `ACTIVE_PR` #306;
- durable asynchronous job persistence, distributed idempotency/backpressure/cancellation and production-grade restart recovery remain issue #312 and are not shipped;
- production identity/federation remains issue #314; the current HMAC-signed gateway-claim mode is not a complete provider-neutral OIDC/JWT enterprise federation contract;
- OpenTelemetry and measured operational acceptance remain incomplete and are part of issue #312's execution evidence;
- the tenant-safe end-to-end deletion/download user journey remains issue #263 after active lifecycle/accessibility/security substrate integrates;
- several high-value security/accessibility/availability branches are stacked behind the current parent and require refreshed exact-head/live-base evidence after integration;
- qualifying independent approval and repository protection still govern integration where required.

This repository does not claim certification, legal title assurance, customer traction, valuation, or transferability merely because engineering evidence exists.

## Product diligence

| Question | Evidence status | Current authority | Acquisition implication |
| --- | --- | --- | --- |
| Can Clearfolio accept and view validated PDFs? | `PROTECTED_MAIN` | `ARCHITECTURE.md`, current source, `docs/PRD.md` | Core viewing/passthrough path exists. |
| Does protected main provide faithful production Office conversion? | `PARTIAL` | issue #5, `docs/FIDELITY_ACCEPTANCE.md`, `ACTIVE_PR` #306 | No. The non-PDF placeholder is not production Office fidelity. |
| Is the Office engine isolated from the API trust boundary? | `ACTIVE_PR` / `PLANNED` | ADR-0005, `docs/UML.md`, issue #5, #306 | Provider-neutral publication boundary is being developed; sandboxed sidecar or independently operated remote runtime still requires qualification. |
| Are accepted conversion jobs durably recoverable with explicit backpressure/cancellation? | `PLANNED` | issue #312, protected-main worker/repository/executor code | No. Protected main is safely bounded but process-local; production durable acceptance/outbox, lease fencing, cancellation and admission semantics remain to implement. |
| Does Clearfolio have provider-neutral production identity federation? | `PLANNED` | issue #314, current tenant-claim verifier/config code, Draft #313 key-strength hardening | No. Current shared-HMAC gateway claims are a deployment adapter; issuer/audience/algorithm/key-rotation/tenant-role federation still requires a versioned verifier contract. |
| Is the complete tenant-safe deletion/download journey shipped? | `PARTIAL` | issue #263, #270/#268/#264 | Lower-layer work exists on active branches; buyer-facing lifecycle completion and integrated recovery evidence remain. |
| Are APIs versioned and host/MSA boundaries explicit? | `PARTIAL` | `docs/API_CONTRACT.md`, `ARCHITECTURE.md`, ADR-0001 | Current boundaries are documented; full generated/public schema completeness remains partial. |

## Architecture and engineering diligence

The current canonical architecture set is:

- `docs/PRD.md`
- `docs/TRD.md`
- root `ARCHITECTURE.md`
- `docs/adr/README.md` and detailed ADRs
- `docs/UML.md`
- `docs/DATA_MODEL.md`
- `docs/API_CONTRACT.md`
- `SECURITY.md`
- `docs/THREAT_MODEL.md`
- `docs/TEST_STRATEGY.md`
- `docs/OPERABILITY.md`
- `docs/TRACEABILITY.md`
- `docs/FIDELITY_ACCEPTANCE.md`
- `docs/MIGRATION_ROLLBACK.md`
- `docs/RELEASE_ACCEPTANCE.md`
- `docs/RESEARCH_TRACEABILITY.md`
- `docs/DOCUMENTATION_ASSESSMENT.md`
- `docs/ACQUISITION_DILIGENCE.md`

The set is `DESIGN_SUFFICIENT` on the active documentation line when its current review findings and exact-head checks are clean. It is not `PROTECTED_MAIN_SUFFICIENT` until that exact documentation head integrates through repository policy.

### Data/persistence truth

`docs/DATA_MODEL.md` is a conceptual/logical ERD and ownership map. It explicitly distinguishes process memory, file artifacts, file ledgers, host-owned identity, `ACTIVE_PR` lifecycle evidence, and `PLANNED` entities. A diagrammed entity does not imply a database table. Protected-main job state remains process-local by default, so distributed durability must not be inferred from filesystem artifact persistence.

Issue #312 is the bounded authority for future general durable conversion-job acceptance, transactional outbox/idempotency, lease-fenced execution, explicit backpressure/cancellation, restart/redelivery recovery and related OpenTelemetry evidence. It must reuse generation/fencing semantics from #268 after integration rather than duplicating them.

Issue #314 is the bounded authority for provider-neutral production identity verification/federation. It must preserve the internal `tenant_context` authorization contract and artifact-token separation instead of copying object authorization into each identity provider adapter.

## Security and privacy diligence

### Current evidence

- root `SECURITY.md` is the current product security entrypoint;
- server-side tenant authorization and same-tenant concealment are explicit design/runtime concerns;
- signed artifact delivery is distinct from metadata permission;
- signed-token issuance, checksum binding, revocation and controlled read audit exist on the canonical artifact path;
- HMAC pseudonymization and purpose/key separation are documented, with active hardening in the security stack;
- uploaded documents and derived content are untrusted;
- Office-process execution inside the API-container trust boundary is rejected by the accepted architecture;
- release acceptance requires security/review/SBOM/provenance evidence rather than one green status.

### Remaining risk

- production issuer/audience/key-rotation/tenant-role identity federation remains issue #314; Draft #313 hardens shared-HMAC production key length but does not close the broader federation gap;
- distributed tenant-safe persistence and centralized audit/retention controls remain incomplete;
- real Office sandbox/malware/active-content/fidelity qualification is not complete;
- privacy/data-retention behavior must be validated against the actual deployment/customer legal basis before production use.

Pseudonymized identifiers remain personal data; Clearfolio does not treat pseudonymization as anonymization.

## Reliability and operations diligence

`docs/OPERABILITY.md`, `docs/MIGRATION_ROLLBACK.md`, and issue #312 are the current operator/recovery authorities for the transition from process-local execution to durable asynchronous operation. They deliberately do not invent SLO/RPO/RTO values.

Material remaining risks include:

- durable asynchronous job state and crash/restart recovery (issue #312);
- idempotent distributed execution and backpressure/cancellation (issue #312);
- OpenTelemetry traces/metrics and measured capacity evidence (issue #312 plus OPERABILITY);
- real backup/restore for future durable state;
- production Office sidecar/remote-service process recycling, cleanup, no-network enforcement and resource-pressure behavior;
- integrated protected-main release acceptance after the current stacked PRs converge.

Until measured deployment-profile evidence exists, availability, recovery-time and throughput claims remain `PARTIAL` or `PLANNED`.

## Supply-chain, license and provenance diligence

### Repository-owned evidence

- root `LICENSE` grants Apache License 2.0 terms for the repository work as published;
- `docs/security/2026-07-02-license-allowlist-review.md` records the repository dependency-license review baseline;
- `docs/legal/2026-07-03-third-party-attribution.md` provides generated third-party attribution evidence;
- `scripts/render_third_party_attribution.py` and related contract tests reduce attribution drift;
- CycloneDX SBOM evidence exists under dated QA evidence and current release policy requires SBOM/provenance tied to the exact release identity;
- issue #5 separately requires the exact Office runtime image, fonts, native libraries, codecs/dictionaries and transitive license obligations to be reviewed before production qualification.

### Acquisition/IP limits

The public repository license is not the same thing as an acquisition-chain **intellectual property** title report. This repository does not independently prove employment invention assignment, contractor assignment, contributor authority, trademark ownership, customer-data rights, patent freedom-to-operate, or all historical contribution provenance. Those matters require **external legal review** and organization records.

Contributor identity in Git history can support diligence, but contributor identity alone does not prove assignment or authority. Consequently, complete transfer-chain evidence is **not independently proven** by the repository and must not be represented as closed acquisition risk.

A production Office distribution also requires legal review of the exact selected runtime and redistribution package. Library-level license compatibility is not sufficient approval for an entire container/runtime image.

## NOTICE and third-party attribution

Apache-2.0 redistribution requires applicable attribution/NOTICE obligations to be preserved. Clearfolio currently generates third-party attribution from reviewed dependency evidence; release acceptance must verify the generated attribution against the exact SBOM/dependency set. If a dependency or Office runtime introduces a NOTICE or source-offer obligation, the release package must carry it explicitly.

Absence of a repository-root `NOTICE` file is not by itself proof that no NOTICE obligation exists. The exact release dependency/runtime inventory is authoritative for that determination.

## Compliance and certification boundary

Clearfolio may design controls toward enterprise assurance and evidence readiness, but this repository **does not claim certification** for SOC 2, CSAP, ISO/IEC 27001/42001, accessibility conformance, or another external scheme merely from code or documentation. Any certification/conformance claim requires the relevant independent assessment and scoped evidence.

Privacy, security and accessibility documentation supports engineering diligence; it does not replace applicable customer, regulator, accessibility or legal review.

## Review, CI and merge diligence

An acquirer should expect exact evidence, not screenshots of old green runs:

- source head and independently resolved live base are separate identities;
- required checks must be terminal-success for the applicable exact candidate;
- queued, pending, cancelled, skipped-required, absent, stale, predecessor, synthetic-only, model-only and status-only evidence is non-passing;
- CodeRabbit/OpenCode/Noema output is advisory/review evidence according to its actual formal state, not automatic approval;
- **independent approval** is counted only when it is a qualifying formal non-author approval under the applicable repository/governance rule;
- a merge is not a release; protected-main operational, fidelity, recovery, SBOM/provenance and publication verification remain distinct gates.

`docs/RELEASE_ACCEPTANCE.md` is the release authority.

## Current open work that materially affects diligence

Live PR and issue numbers and heads must be refetched before a transaction or release decision. At this assessment date, the material work families include:

- #270 — security/privacy/test-evidence and signed direct-download parent;
- #264 — nested-safe accessible async viewer controls, stacked behind #270;
- #268 — signed admin tenant/lifecycle/deletion recovery, stacked behind #270;
- #271 — repository-local OpenCode development-loop controls, stacked behind #270 and central `.github` dependency;
- #276 — malformed artifact-token structure hardening, stacked behind #270;
- #295 — liveness/readiness separation, stacked behind #270;
- #305 — canonical documentation and acquisition-truth line;
- #306 / issue #5 — provider-neutral Office adapter and fail-closed qualification work;
- #311 — Junrar dependency-management update; exact-head checks are useful source evidence but it overlaps the parent dependency/SBOM surface and must be reconciled after the stable security parent before integration;
- #313 — production signed-tenant HMAC key-length readiness hardening; this is not equivalent to full identity federation;
- issue #312 — durable job acceptance/outbox/idempotency/backpressure/cancellation/restart/observability work, dependency-aware behind the security/lifecycle baseline;
- issue #314 — provider-neutral production OIDC/JWT identity federation and tenant-role mapping, dependency-aware behind overlapping authentication/security work.

These numbers are navigation hints, not permanent evidence. Any head/base movement invalidates predecessor-head merge conclusions.

## Historical evidence policy

The following remain useful only as dated **historical evidence** unless explicitly refreshed against current code:

- `docs/diligence/2026-07-02-buyer-diligence-index.md`;
- `docs/diligence/2026-07-03-buyer-readiness-scorecard.md`;
- `docs/diligence/2026-07-03-buyer-data-room-manifest.json`;
- `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/`;
- dated buyer-demo/FigJam/Slides artifacts and screenshots;
- stale PR numbers, SHAs, local coverage logs, or queued-check narratives embedded in those artifacts.

They must not be used to assert that PR #74 is the current baseline or that PR #82 is the current active product line. Current GitHub refs, protected source, canonical docs and exact evidence supersede them.

## External diligence still required before an acquisition claim

Repository engineering cannot autonomously close every transaction risk. The buyer/seller must separately obtain or verify, as applicable:

- corporate authority and cap-table/transaction authority;
- employment/contractor/contributor intellectual property assignment chain;
- trademark/domain ownership and naming conflicts;
- patent/freedom-to-operate analysis where material;
- third-party commercial/runtime distribution rights;
- customer contracts, DPAs, processor/subprocessor obligations and data-transfer rights;
- production infrastructure/cloud/secret/KMS/data-residency evidence;
- incident/legal/regulatory history;
- independent security/compliance/accessibility assessments if claimed;
- customer/pilot/usage/revenue evidence and valuation assumptions.

These are `EXTERNAL` evidence categories. Their absence from source control is not a software bug, but the repository must not imply they are complete.

## Acquisition readiness exit criteria

Engineering may describe the software package as acquisition-ready only when, at minimum:

1. canonical docs are `PROTECTED_MAIN_SUFFICIENT`;
2. issue #5's production Office qualification and realistic fidelity/security evidence are integrated for every advertised transformed format, or those formats are explicitly unsupported;
3. issue #312's durable asynchronous lifecycle/backpressure/cancellation/restart recovery and tenant-safe persistence have release-grade evidence;
4. issue #314's production identity/federation boundary is integrated for the deployment profile, with privacy, retention, audit and authorization evidence;
5. issue #263's user-facing tenant-safe lifecycle is complete where included in product scope;
6. exact integrated CI/security/coverage/Javadocs/fidelity/accessibility/package/SBOM/provenance/migration/recovery/review gates pass;
7. the released artifact is verified after publication; and
8. the seller/buyer completes the required external legal/IP/commercial diligence without the repository claiming what it cannot prove.

Until those conditions are met, the correct status is substantial engineering evidence with explicit remaining risk—not a finished acquisition-ready product.
