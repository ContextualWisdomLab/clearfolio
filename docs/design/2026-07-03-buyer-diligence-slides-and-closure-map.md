# Buyer Diligence Slides and Closure Map Handoff

Date: 2026-07-03

## Purpose

This handoff turns the Clearfolio KRW 2B sale-readiness program into a
buyer-review artifact set. It is not a valuation opinion. It defines what a
buyer can inspect now, what remains a gap, and which evidence should be closed
next before buyer-release claims are made.

Figma Code Connect is not used.

## Figma Artifacts

- FigJam board:
  [Clearfolio Buyer Demo Evidence Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM)
- Added FigJam diagram on the same board:
  `Clearfolio KRW 2B Buyer Diligence Closure Map`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Readiness Scorecard Gate Map`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Diligence Slides Storyboard`.
- Existing seeded screenshot nodes on the same board:
  desktop `25:1423`, mobile `25:1422`.
- Figma Slides generation payload:
  `docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json`.
- Figma Slides payload check:
  `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/figma-deck-payload-check.json`.
- Figma Slides generation status:
  attempted from the Figma tool, but generation requires Figma team or
  organization plan selection in the widget before the deck can be created.

## Product Design Brief

Audience:

- Enterprise platform leaders evaluating integration cost.
- Product and design reviewers evaluating demo coherence.
- Security, compliance, and acquisition reviewers evaluating diligence risk.

Design source:

- Running local Clearfolio Viewer after clicking `Load demo story`.
- Desktop and mobile screenshots under
  `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/screenshots/`.
- FigJam screenshot nodes `25:1423` and `25:1422`.
- Existing buyer-demo root shell, KPI strip, evidence panel, recovery panel,
  job detail drawer, and session history.

Acceptance:

- The first viewport must show product identity, upload action, and KPI proof.
- The demo story must include success, processing, unsupported-format, and
  dead-letter states.
- KPI copy must stay buyer-readable and map to implementation fields.
- Gap labels must remain explicit; partial evidence must not be styled as
  complete.
- Static seeded demo evidence must be labeled as local demo evidence, not
  production data.

## Data Analytics Frame

| Metric | Current proof | Sale-readiness threshold | Caveat |
| --- | --- | --- | --- |
| Runtime jobs | Seeded demo shows 4 jobs | 50K monthly documents after pilot phase | Current proof is local runtime/demo scope. |
| Ready previews | Seeded demo shows 1 ready preview | Increasing share of total jobs | Durable tenant history is not implemented. |
| Successful preview rate | Seeded demo shows 50% across mixed states | 99.5% for supported pilot formats | Seed includes negative states by design. |
| P95 preview latency | Seeded demo shows 4200 ms | Less than 10 seconds for small pilot files | Needs real pilot files for production claim. |
| KPI snapshot export | Seeded demo shows 1 export | Repeatable tenant-scoped evidence exports | Current ledger is optional local replay evidence. |
| Commercial proof | Pricing scenarios documented | 3 design partners, then 10 paid tenants | No pilots, LOIs, or buyer import test yet. |
| Evidence readiness | Scorecard reports 23 artifacts, 8 gates, and 38 percent conservative gate readiness | 100 percent ready gates before buyer-release claim | Current score is repository evidence readiness, not valuation proof. |
| Slides generation readiness | Payload check reports 11 slides, 4 objectives, and 0 errors | Editable Figma Slides deck generated after plan selection | Current payload is ready; actual Slides URL is pending Figma plan selection. |

## Ponytail Architecture Decision

Do not split a separate library, Git submodule, or SDK package in this slice.

Rationale:

- There is one real runtime and no second production consumer.
- A submodule would add diligence friction without increasing buyer value.
- Current buyer risk is evidence, legal clearance, identity validation,
  durability, and commercial proof, not code packaging.
- Keep controller, service, repository, artifact, analytics, and auth
  boundaries in-repo until converter adapters, analytics contracts, or SDK
  clients need independent versioning.

## Slides Deck Plan

When Figma plan selection is available, generate the deck from
`docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json` with
this structure:

1. Cover: `Clearfolio Buyer Demo Diligence Pack`.
2. What the buyer can inspect today: upload, governed preview, operational
   proof.
3. Seeded demo proof state: 4 jobs, 1 ready preview, 50% mixed-state success,
   4200 ms p95, 1 KPI export.
4. Buyer demo journey: root shell, seeded story or upload path, evidence panel,
   job detail, viewer, recovery posture.
5. Diligence evidence map: gates, security, compliance, integration.
6. Buyer readiness scorecard: ready evidence, partial discount risks, and claim
   boundary.
7. KRW 2B operating hurdle: KRW 400M to KRW 650M ARR path or equivalent
   strategic licensing value.
8. Why no library or submodule split now.
9. Remaining gaps that still discount the asset.
10. Next closure order.
11. Buyer-readiness claim boundary.

## Next Closure Order

1. Keep buyer-release license-policy evidence and attribution drift checks
   green, then obtain final legal release review.
2. Import-test the connector seed in a buyer tenant or buyer-like sandbox.
3. Replace gateway HMAC tenant scaffolding with validated OIDC/JWT claims,
   role mapping, and managed secret rotation.
4. Promote process-local lifecycle events, KPI snapshot ledger evidence,
   artifact metadata, revocation, and read audit into durable stores.
5. Produce high-fidelity Figma frames from seeded desktop/mobile screenshot
   states and production-ready negative paths.

## Claim Boundaries

- KRW 2B is a sale-readiness target, not a valuation opinion or fairness
  opinion.
- Seeded screenshots are deterministic local demo evidence, not production
  data.
- Runtime KPI snapshots are not durable analytics until the event model is
  implemented with persistent projections.
- The readiness scorecard measures evidence readiness only; it is not a
  valuation opinion or production-readiness certification.
- Gateway HMAC tenant headers are an integration scaffold, not production
  OIDC/JWT.
- Review process and queued GitHub checks are not blockers for continuing
  evidence-building work.
