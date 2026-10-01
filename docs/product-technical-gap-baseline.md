# Clearfolio Product and Technical Gap Baseline

Status: **Proposed**

This document is the canonical, reconstructable baseline for buyer-visible product and
technical gaps in Clearfolio. It records only evidence that can be traced to a protected
branch, pull request, exact commit, workflow run, API contract, or reproducible experiment.

## Goal and loop

Clearfolio is the product owner for document intake, conversion, viewing, and tenant-safe
artifact delivery. The development loop is: review every open pull request, repair findings
at their causal owner, verify the exact head, merge through ordinary protection, then select
the next highest-value gap.

## Context map and ownership

| Context | Responsibility | Current boundary |
| --- | --- | --- |
| Document intake | Upload UX and request validation | Clearfolio application |
| Document conversion | Job lifecycle, conversion, and artifact creation | Clearfolio application |
| Document viewing | Signed same-origin artifact rendering | Clearfolio application |
| Identity and authorization | Token issuance and federation | Keyverse contract through an ACL |
| Shared CI and admission | Reusable review, security, and release policy | ContextualWisdomLab/.github |
| Contract conformance | Shared assertion, event, schema, and fixture conformance | context-graph-contracts releases |

Product domain truth remains in Clearfolio. Shared owners are consumed through released
contracts and anti-corruption layers; Clearfolio does not read their source databases or
temporary branches.

## Architecture evidence

| Artifact | Evidence | Status |
| --- | --- | --- |
| PRD / buyer goal | `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md` | Existing evidence; consolidation remains Proposed |
| TRD | `docs/trd-integrated-document-viewer-platform.md` | Existing |
| Buyer diligence index | `docs/diligence/2026-07-02-buyer-diligence-index.md` | Existing |
| ADR | Repository ADR records and executable drift tests | Existing; map completeness remains Proposed |
| UML / ERD | No complete canonical index is evidenced by PR #663 | Gap |
| Context map | Ownership table in this baseline | Proposed |

## Gap and action register

| Gap | Exact evidence | Action | Status |
| --- | --- | --- | --- |
| Required document input was not visibly identified | PR #663, original head `38b9150fe8d29e2dfdbb879db5599b2b8c6db191` | Render `Document (required)` inside the grid-safe span and retain response regression coverage | Implemented; browser accessibility evidence remains Proposed |
| Jackson 2.22.1 contained newly disclosed denial-of-service vulnerabilities | Canonical dependency PR #503, exact head `bf121dffff24e771739f50b9e2cc7961459670a2`; the duplicate #663 dependency delta was removed at `80eb0a73f9fbeff7a7f9652d08946c2bdad7cd54` | Complete, verify, and merge the dependency repair at #503; consume only its protected result | In progress at canonical owner; excluded from #663 |
| CodeQL was skipped on predecessor Draft head `80eb0a73f9fbeff7a7f9652d08946c2bdad7cd54` | PR #663, workflow run 36926377953 | Keep the PR Draft while UI evidence is incomplete; central stale-event repair remains owned by ContextualWisdomLab/.github#2537 | Non-evidence preserved; no bypass or synthetic wake event |
| Canonical UML / ERD index is absent from the evidenced document set | This baseline and repository documentation search on 2026-10-02 | Add only diagrams backed by current code, schema, and API evidence | Proposed |

## Verification rules

- A check result is attributed only to the exact commit it executed.
- Dependency and security changes require a failing regression or scanner reproduction
  followed by CI, Trivy, OSV, dependency-review, and SAST evidence.
- Draft is reserved for a source, policy, ownership, conflict, or mutable-prerequisite defect.
  Missing approval or a nonterminal check blocks merge, not review admission.
- No merge, bypass, release, or completion claim is made while exact-head evidence is
  missing or a substantive finding remains unresolved.
