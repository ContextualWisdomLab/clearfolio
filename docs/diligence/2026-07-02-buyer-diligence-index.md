# Buyer Diligence Index

Date: 2026-07-02

This index maps buyer diligence questions to current Clearfolio evidence,
known gaps, and the next artifact that should close each gap. It is intentionally
strict: partial evidence is marked partial, not complete.

## Status Legend

- `Ready`: current repo evidence is strong enough for a buyer walkthrough.
- `Partial`: useful evidence exists, but a buyer would still discount the risk.
- `Missing`: no durable evidence exists yet.

## Product and Demo Evidence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Can a buyer run a demo from upload to preview? | Ready | `GET /`, `POST /api/v1/convert/jobs`, `/viewer/{docId}`; local smoke proof in PR #74. | Demo uses in-memory runtime. | Seeded demo data and screenshot set. |
| Does the UI expose buyer-readable KPIs? | Ready | `GET /api/v1/analytics/kpi-snapshot`; root shell reads tenant-scoped runtime KPI snapshot and renders a KPI snapshot evidence panel from `GET /api/v1/analytics/kpi-snapshot-exports`; optional `clearfolio.analytics-snapshot-ledger.path` records exported snapshots; durable model in `docs/analytics/2026-07-02-durable-metrics-event-model.md`. | Full lifecycle KPI history is not implemented durably yet. | Durable metric event implementation. |
| Is the Figma design story available without Code Connect? | Ready | FigJam evidence flow and `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md`. | High-fidelity screen frames are not complete. | Figma frames for desktop/mobile happy and negative paths. |
| Are unsupported and failed states explained? | Partial | HWP/HWPX block behavior, error schema, failed job retry flow, buyer-demo status table, root-shell job detail drawer, and session-scoped operator recovery evidence panel. | Retry is surfaced in the buyer-demo shell, but not yet as a production admin UI. | Production operator job management surface. |

## Technical Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is the architecture inspectable? | Ready | `docs/architecture.md`, PRD/TRD, diagrams, package boundaries. | Target production architecture is still partly roadmap. | Target architecture diagram for durable queue/store. |
| Are the mandatory gates reproducible? | Ready | Maven compile/test/JaCoCo/JavaDoc commands in PR #74, AGENTS gate policy, and `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`. | GitHub hosted checks are queued. | Attach CI pass or queued-check explanation when available. |
| Is code coverage at the required threshold? | Ready | PR #74 local JaCoCo: `classes=48`, `line_missed=0`, `branch_missed=0`. | CI checks are queued at latest head. | Attach CI pass or queued-check explanation when available. |
| Is request handling non-blocking? | Ready | WebFlux controller path and `DefaultDocumentConversionService` enqueue behavior. | Real converter runtime is not integrated. | Converter adapter contract and load-test plan. |
| Is persistence production-grade? | Partial | `ConversionJobRepository` read/dedupe/recovery abstraction, `ConversionJobStateStore` lifecycle transition boundary, worker startup recovery sweep for due `SUBMITTED` and stale retryable `PROCESSING` jobs, and process-local `ConversionJobLifecycleEvent` trail exist in code; `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md` defines target tables, transition events, worker recovery, and migration sequence. | Durable SQL implementation is not built, lifecycle events are still process-local, and process restart still loses in-memory repository state. | SQL repository profile, durable restart recovery contract tests, and durable event projections. |
| Are artifacts production-grade? | Partial | In-memory PDF artifact store, signed artifact-token runtime, optional file-backed artifact link ledger replay, tenant-scoped token revocation, artifact read audit API, range-serving controller, and `docs/security/2026-07-02-signed-artifact-link-design.md`. | No durable object store, centralized revocation table, centralized read audit, or retention policy. | Durable artifact metadata and centralized revocation/audit implementation. |

## Security and Compliance Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there SAST evidence? | Ready | Semgrep evidence under `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json`; 0 findings. | GitHub security checks are queued on PR #74. | Check-run snapshot when workflows complete. |
| Are risky formats controlled? | Ready | HWP/HWPX default block, policy-override headers with token fingerprint logging, and `docs/security/2026-07-02-threat-model-data-handling.md`. | Policy ownership and approval workflow are not externalized. | Policy-owner matrix. |
| Are browser security headers present? | Ready | `ViewerSecurityHeadersWebFilter` applies viewer browser headers. | CSP/frame policy still needs production domain matrix. | Deployment security profile. |
| Is auth/RBAC implemented? | Partial | Header-claim runtime enforcement exists for JSON APIs and artifact links: `TenantAccessService`, tenant-owned `ConversionJob`, tenant-aware dedupe, cross-tenant `404`, tenant-filtered KPI snapshots, optional gateway HMAC validation for tenant headers, signed artifact-token reads, token revocation, optional file-backed artifact ledger replay, and artifact read audit events. Auth/tenant design exists in `docs/security/2026-07-02-auth-tenant-model.md`. | OIDC/JWT issuer/audience/expiry validation, role mapping, managed secret rotation, and centralized audit events are not implemented. | Validated gateway/OIDC claims plus durable audit/revocation store. |
| Is there license/SBOM evidence? | Partial | CycloneDX SBOM evidence exists under `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/`; engineering review exists in `docs/security/2026-07-02-license-allowlist-review.md`; `scripts/check_sbom_license_policy.py` enforces the engineering allowlist and current policy summary reports 136 allowed components, 6 review-required components, and 0 unlisted violations. | Six flagged components still need legal approve, replace, or remove decisions before buyer-release mode can require zero review-required components. | Legal sign-off and buyer-release license-policy mode. |
| Is data handling documented? | Partial | `docs/security/2026-07-02-threat-model-data-handling.md` maps current data classes, trust boundaries, and retention limits. | Production retention policy, tenant ACLs, and durable encrypted stores are not implemented. | Production data-retention policy. |

## Commercial Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there a KRW 2B valuation logic? | Ready | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md`. | Comparable transactions are not refreshed beyond public multiple anchors. | Transaction comparable refresh before buyer use. |
| Is there a pricing path? | Partial | Pricing scenarios in valuation/KPI model. | No customer interviews, pilots, or signed LOIs. | Pilot evidence and ICP qualification pack. |
| Are buyer KPIs measurable? | Partial | Runtime KPI snapshot exposes reliability and latency fields, is filtered by request tenant, can record exported snapshots to an optional local ledger, exposes those exports through a tenant-scoped evidence API, renders latest export evidence in the buyer-demo UI, and the in-memory repository now records process-local lifecycle events for transition traceability; durable event model is documented in `docs/analytics/2026-07-02-durable-metrics-event-model.md`. | Durable lifecycle event persistence, projections, monthly volume, cost, and margin data are not implemented. | Durable analytics event implementation. |
| Can a buyer integrate it cheaply? | Ready | API routes, Power Platform delivery chain, `src/main/resources/application-buyer-demo.yml`, `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`, and `docs/deployment/clearfolio-buyer-connector.openapi.yaml` are documented. | Connector seed has not been imported into a buyer tenant, and production OIDC/JWT deployment profile is not complete. | Buyer-specific connector import test and production gateway/OIDC profile. |

## Current PR Evidence

| Evidence item | Location |
| --- | --- |
| Merged baseline PR | <https://github.com/ContextualWisdomLab/clearfolio/pull/74> |
| Current recovery-sweep branch | `codex/durable-conversion-recovery` |
| Sale-readiness plan | `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md` |
| Business model | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md` |
| FigJam handoff | `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md` |
| Threat model and data handling | `docs/security/2026-07-02-threat-model-data-handling.md` |
| Signed artifact design | `docs/security/2026-07-02-signed-artifact-link-design.md` |
| Auth and tenant model | `docs/security/2026-07-02-auth-tenant-model.md` |
| License allowlist review | `docs/security/2026-07-02-license-allowlist-review.md` |
| License policy checker | `docs/security/2026-07-02-license-policy.json`, `scripts/check_sbom_license_policy.py`, `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/license-policy-summary.json` |
| Durable metrics event model | `docs/analytics/2026-07-02-durable-metrics-event-model.md` |
| SBOM evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json`, `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-status.txt` |
| SAST evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json` |
| Buyer-demo implementation | `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`, `src/main/resources/static/assets/viewer/demo.js`, `src/main/resources/static/assets/viewer/viewer.css`; includes KPI evidence and session-scoped operator recovery evidence panels. |
| KPI API implementation | `src/main/java/com/clearfolio/viewer/controller/AnalyticsController.java`, `src/main/java/com/clearfolio/viewer/api/KpiSnapshotResponse.java`, `src/main/java/com/clearfolio/viewer/api/KpiSnapshotExportResponse.java` |
| KPI snapshot evidence ledger | `src/main/java/com/clearfolio/viewer/analytics/KpiSnapshotLedger.java`, `src/main/java/com/clearfolio/viewer/analytics/KpiSnapshotRecord.java`; includes optional `clearfolio.analytics-snapshot-ledger.path` file-backed replay and tenant-scoped lookup for exported KPI snapshots. |
| Auth/tenant runtime slice | `src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java`, `src/main/java/com/clearfolio/viewer/auth/TenantContext.java`, `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`, `src/main/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepository.java`; includes optional gateway HMAC validation when `clearfolio.tenant-claims.hmac-secret` is set. |
| Signed artifact runtime slice | `src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkService.java`, `src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkLedger.java`, `src/main/java/com/clearfolio/viewer/controller/ArtifactController.java`, `src/main/java/com/clearfolio/viewer/api/ArtifactLinkResponse.java`; includes optional `clearfolio.artifact-link-ledger.path` file-backed replay for issued/revoked/read metadata. |
| Buyer deployment integration pack | `src/main/resources/application-buyer-demo.yml`, `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`, `docs/deployment/clearfolio-buyer-connector.openapi.yaml`; includes buyer sandbox profile, gateway-signed header contract, connector API table, OpenAPI connector seed, smoke path, and cutover gates. |
| Durable job repository design, state-store, lifecycle event, and recovery sweep slice | `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`, `src/main/java/com/clearfolio/viewer/repository/ConversionJobRepository.java`, `src/main/java/com/clearfolio/viewer/repository/ConversionJobStateStore.java`, `src/main/java/com/clearfolio/viewer/repository/ConversionJobLifecycleEvent.java`, `src/main/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepository.java`, `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`; includes SQL target tables, lifecycle transition contract, process-local append-only event trail, worker startup recovery sweep, repository API change sequence, buyer acceptance criteria, and the first in-repo transition boundary implementation. |

## Next Closure Order

1. Get legal sign-off or replacement decisions for the six review-required SBOM
   components, then run license-policy buyer-release mode.
2. Use configured gateway-signed tenant headers for buyer deployments, then
   replace the scaffold with validated gateway/OIDC JWT claims.
3. Import-test the connector seed and add a buyer-specific gateway/OIDC
   deployment profile once buyer infrastructure details are known.
4. Promote optional file-backed artifact-link ledger evidence into durable
   artifact metadata and centralized token revocation plus artifact read audit
   persistence.
5. Promote the process-local conversion recovery sweep, lifecycle events, and
   optional KPI snapshot ledger evidence into durable metrics events and daily
   projections.
6. Add seeded demo screenshots and Figma high-fidelity frames.
