# Clearfolio Product and Technical Gap Baseline

Status: **Proposed**

This baseline records only evidence traceable to a protected branch, pull request, exact commit, workflow run, API contract, or reproducible experiment.

## Goal and loop

Clearfolio owns document intake, conversion, viewing, and tenant-safe artifact delivery. Review every open pull request, repair findings at the causal owner, verify the exact head, merge through ordinary protection, then select the next highest-value gap.

## Context map and ownership

| Context | Responsibility | Boundary |
| --- | --- | --- |
| Document intake | Upload UX and request validation | Clearfolio |
| Document conversion | Job lifecycle and artifact creation | Clearfolio |
| Document viewing | Signed same-origin rendering | Clearfolio |
| Identity and authorization | Token issuance and federation | Keyverse release through an ACL |
| Shared CI and admission | Review, security, and release policy | ContextualWisdomLab/.github |
| Contract conformance | Assertion, event, schema, and fixture conformance | context-graph-contracts releases |

Product domain truth remains in Clearfolio. Shared owners are consumed through released contracts and anti-corruption layers; Clearfolio does not read their source databases or temporary branches.

## Architecture evidence

| Artifact | Evidence | Status |
| --- | --- | --- |
| PRD / buyer goal | `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md` | Existing; consolidation Proposed |
| TRD | `docs/trd-integrated-document-viewer-platform.md` | Existing |
| Buyer diligence index | `docs/diligence/2026-07-02-buyer-diligence-index.md` | Existing |
| ADR | Repository ADR records and executable drift tests | Existing; map completeness Proposed |
| UML / ERD | No complete canonical index evidenced by PR #663 | Gap |
| Context map | Ownership table above | Proposed |

## Gap and action register

| Gap | Exact evidence | Action | Status |
| --- | --- | --- | --- |
| Required document input was not visibly identified | PR #663 original head `38b9150fe8d29e2dfdbb879db5599b2b8c6db191` | Render `Document (required)` in a grid-safe span and retain response coverage | Implemented; browser accessibility evidence Proposed |
| Jackson 2.22.1 vulnerabilities | Canonical PR #503 exact head `bf121dffff24e771739f50b9e2cc7961459670a2`; partial leaf copy reappeared at #663 `97dc021016d97636aabf581c9c0dfacedf13e710` | Complete and merge at #503; consume only its protected result | In progress at canonical owner; excluded from #663 |
| Concurrent writer overlap | #663 writer repeatedly restored generated journal/partial POM while #662 claimed successor ownership | Preserve UI/test delta; remove non-owner files by ordinary commits; do not retire #663 until every valid delta is proven integrated | Proposed / monitored |
| Canonical UML / ERD index absent | This baseline and repository documentation search on 2026-10-02 | Add only diagrams backed by current code, schema, and API evidence | Proposed |

## Verification rules

- Attribute a check only to the exact commit it executed.
- Dependency and security changes require RED reproduction, owner repair, scanner evidence, protected merge, immutable release, and consumer adoption.
- Missing approval or a nonterminal check blocks merge, not review admission.
- Do not claim merge, release, publication, or completion while exact-head evidence or a substantive finding is missing.
