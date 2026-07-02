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
| Does the UI expose buyer-readable KPIs? | Ready | `GET /api/v1/analytics/kpi-snapshot`; root shell reads runtime KPI snapshot; durable model in `docs/analytics/2026-07-02-durable-metrics-event-model.md`. | KPI history is not implemented durably yet. | Durable metric event implementation. |
| Is the Figma design story available without Code Connect? | Ready | FigJam evidence flow and `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md`. | High-fidelity screen frames are not complete. | Figma frames for desktop/mobile happy and negative paths. |
| Are unsupported and failed states explained? | Partial | HWP/HWPX block behavior, error schema, failed job retry flow, buyer-demo status table. | Operator retry is not yet surfaced as an admin UI. | Operator job detail drawer design and implementation slice. |

## Technical Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is the architecture inspectable? | Ready | `docs/architecture.md`, PRD/TRD, diagrams, package boundaries. | Target production architecture is still partly roadmap. | Target architecture diagram for durable queue/store. |
| Are the mandatory gates reproducible? | Ready | Maven compile/test/JaCoCo/JavaDoc commands in PR #74, AGENTS gate policy, and `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`. | GitHub hosted checks are queued. | Attach CI pass or queued-check explanation when available. |
| Is code coverage at the required threshold? | Ready | PR #74 local JaCoCo: `classes=32`, `line_missed=0`, `branch_missed=0`. | CI checks are queued at latest head. | Attach CI pass or queued-check explanation when available. |
| Is request handling non-blocking? | Ready | WebFlux controller path and `DefaultDocumentConversionService` enqueue behavior. | Real converter runtime is not integrated. | Converter adapter contract and load-test plan. |
| Is persistence production-grade? | Missing | `ConversionJobRepository` abstraction exists. | In-memory repository only. | Durable repository design and migration plan. |
| Are artifacts production-grade? | Partial | In-memory PDF artifact store, range-serving controller, and `docs/security/2026-07-02-signed-artifact-link-design.md`. | No durable object store, signed URL runtime, retention, or tenant isolation. | Signed artifact link implementation. |

## Security and Compliance Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there SAST evidence? | Ready | Semgrep evidence under `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json`; 0 findings. | GitHub security checks are queued on PR #74. | Check-run snapshot when workflows complete. |
| Are risky formats controlled? | Ready | HWP/HWPX default block, policy-override headers with token fingerprint logging, and `docs/security/2026-07-02-threat-model-data-handling.md`. | Policy ownership and approval workflow are not externalized. | Policy-owner matrix. |
| Are browser security headers present? | Ready | `ViewerSecurityHeadersWebFilter` applies viewer browser headers. | CSP/frame policy still needs production domain matrix. | Deployment security profile. |
| Is auth/RBAC implemented? | Missing | PRD defines S2S/user-context target. | No tenant, RBAC, or signed S2S session model in runtime. | Auth/RBAC and tenant model design. |
| Is there license/SBOM evidence? | Partial | CycloneDX SBOM evidence exists under `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/`; engineering review exists in `docs/security/2026-07-02-license-allowlist-review.md`. | Six flagged components still need legal approve, replace, or remove decisions. | Legal sign-off and CI allowlist enforcement. |
| Is data handling documented? | Partial | `docs/security/2026-07-02-threat-model-data-handling.md` maps current data classes, trust boundaries, and retention limits. | Production retention policy, tenant ACLs, and durable encrypted stores are not implemented. | Production data-retention policy. |

## Commercial Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there a KRW 2B valuation logic? | Ready | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md`. | Comparable transactions are not refreshed beyond public multiple anchors. | Transaction comparable refresh before buyer use. |
| Is there a pricing path? | Partial | Pricing scenarios in valuation/KPI model. | No customer interviews, pilots, or signed LOIs. | Pilot evidence and ICP qualification pack. |
| Are buyer KPIs measurable? | Partial | Runtime KPI snapshot exposes reliability and latency fields; durable event model is documented in `docs/analytics/2026-07-02-durable-metrics-event-model.md`. | Durable event persistence, tenant dimension, monthly volume, cost, and margin data are not implemented. | Durable analytics event implementation. |
| Can a buyer integrate it cheaply? | Partial | API routes and Power Platform delivery chain are documented. | No deployment playbook or connector guide. | Integration and deployment playbook. |

## Current PR Evidence

| Evidence item | Location |
| --- | --- |
| Active PR | <https://github.com/ContextualWisdomLab/clearfolio/pull/74> |
| Sale-readiness plan | `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md` |
| Business model | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md` |
| FigJam handoff | `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md` |
| Threat model and data handling | `docs/security/2026-07-02-threat-model-data-handling.md` |
| Signed artifact design | `docs/security/2026-07-02-signed-artifact-link-design.md` |
| License allowlist review | `docs/security/2026-07-02-license-allowlist-review.md` |
| Durable metrics event model | `docs/analytics/2026-07-02-durable-metrics-event-model.md` |
| SBOM evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json`, `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-status.txt` |
| SAST evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json` |
| Buyer-demo implementation | `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`, `src/main/resources/static/assets/viewer/demo.js` |
| KPI API implementation | `src/main/java/com/clearfolio/viewer/controller/AnalyticsController.java`, `src/main/java/com/clearfolio/viewer/api/KpiSnapshotResponse.java` |

## Next Closure Order

1. Get legal sign-off or replacement decisions for flagged SBOM components.
2. Implement signed artifact links after auth and tenant design.
3. Implement durable metrics events.
4. Add seeded demo screenshots and Figma high-fidelity frames.
5. Add production retention policy and policy-owner matrix.
