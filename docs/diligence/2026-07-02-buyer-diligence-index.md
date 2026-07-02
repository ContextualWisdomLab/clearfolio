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
| Does the UI expose buyer-readable KPIs? | Ready | `GET /api/v1/analytics/kpi-snapshot`; root shell reads runtime KPI snapshot. | KPI history is not durable. | Durable metric event design. |
| Is the Figma design story available without Code Connect? | Ready | FigJam evidence flow and `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md`. | High-fidelity screen frames are not complete. | Figma frames for desktop/mobile happy and negative paths. |
| Are unsupported and failed states explained? | Partial | HWP/HWPX block behavior, error schema, failed job retry flow, buyer-demo status table. | Operator retry is not yet surfaced as an admin UI. | Operator job detail drawer design and implementation slice. |

## Technical Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is the architecture inspectable? | Ready | `docs/architecture.md`, PRD/TRD, diagrams, package boundaries. | Target production architecture is still partly roadmap. | Target architecture diagram for durable queue/store. |
| Are the mandatory gates reproducible? | Ready | Maven compile/test/JaCoCo/JavaDoc commands in PR #74 and AGENTS gate policy. | Evidence folder still has older `LATEST.md` pointer. | Fresh 2026-07-02 release evidence bundle. |
| Is code coverage at the required threshold? | Ready | PR #74 local JaCoCo: `classes=32`, `line_missed=0`, `branch_missed=0`. | CI checks are queued at latest head. | Attach CI pass or queued-check explanation when available. |
| Is request handling non-blocking? | Ready | WebFlux controller path and `DefaultDocumentConversionService` enqueue behavior. | Real converter runtime is not integrated. | Converter adapter contract and load-test plan. |
| Is persistence production-grade? | Missing | `ConversionJobRepository` abstraction exists. | In-memory repository only. | Durable repository design and migration plan. |
| Are artifacts production-grade? | Missing | In-memory PDF artifact store and range-serving controller exist. | No durable object store, signed URLs, retention, or tenant isolation. | Signed artifact link design. |

## Security and Compliance Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there SAST evidence? | Ready | Semgrep evidence under `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json`; 0 findings. | GitHub security checks are queued on PR #74. | Check-run snapshot when workflows complete. |
| Are risky formats controlled? | Ready | HWP/HWPX default block and policy-override headers with token fingerprint logging. | Policy ownership and approval workflow are not externalized. | Threat model and policy-owner matrix. |
| Are browser security headers present? | Ready | `ViewerSecurityHeadersWebFilter` applies viewer browser headers. | CSP/frame policy still needs production domain matrix. | Deployment security profile. |
| Is auth/RBAC implemented? | Missing | PRD defines S2S/user-context target. | No tenant, RBAC, or signed S2S session model in runtime. | Auth/RBAC and tenant model design. |
| Is there license/SBOM evidence? | Missing | OSS references and disallowed AGPL note exist in docs. | No generated SBOM or license scan artifact. | CycloneDX or Maven license evidence. |
| Is data handling documented? | Partial | PRD describes no raw PII requirement for user context; NUL sanitization exists. | No data-flow map or retention classification. | Data handling map and retention policy. |

## Commercial Diligence

| Buyer question | Status | Current evidence | Gap | Next artifact |
| --- | --- | --- | --- | --- |
| Is there a KRW 2B valuation logic? | Ready | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md`. | Comparable transactions are not refreshed beyond public multiple anchors. | Transaction comparable refresh before buyer use. |
| Is there a pricing path? | Partial | Pricing scenarios in valuation/KPI model. | No customer interviews, pilots, or signed LOIs. | Pilot evidence and ICP qualification pack. |
| Are buyer KPIs measurable? | Partial | Runtime KPI snapshot exposes reliability and latency fields. | No tenant, retention, monthly volume, cost, or margin data. | Durable analytics event model. |
| Can a buyer integrate it cheaply? | Partial | API routes and Power Platform delivery chain are documented. | No deployment playbook or connector guide. | Integration and deployment playbook. |

## Current PR Evidence

| Evidence item | Location |
| --- | --- |
| Active PR | <https://github.com/ContextualWisdomLab/clearfolio/pull/74> |
| Sale-readiness plan | `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md` |
| Business model | `docs/business/2026-07-02-krw2b-valuation-kpi-model.md` |
| FigJam handoff | `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md` |
| SAST evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json` |
| Buyer-demo implementation | `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`, `src/main/resources/static/assets/viewer/demo.js` |
| KPI API implementation | `src/main/java/com/clearfolio/viewer/controller/AnalyticsController.java`, `src/main/java/com/clearfolio/viewer/api/KpiSnapshotResponse.java` |

## Next Closure Order

1. Create a fresh 2026-07-02 evidence bundle and update `docs/qa/evidence/LATEST.md`.
2. Add threat model and data handling map.
3. Add SBOM/license evidence.
4. Add signed artifact link design.
5. Add durable metrics event model.
