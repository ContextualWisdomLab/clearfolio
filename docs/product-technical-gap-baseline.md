# Product and Technical Gap Baseline

Status: **Proposed**

Evidence cutoff: 2026-10-01 UTC

Evidence source head: `7b90d4f761aeac89d143a225999f4dbdb44c587e` on
[clearfolio#659](https://github.com/ContextualWisdomLab/clearfolio/pull/659).

## Goal and bounded context

Clearfolio owns the document-viewer bounded context: authenticated document conversion status,
artifact preview, and administrator recovery operations. Product-domain truth remains in Clearfolio.
Identity claims enter through the tenant-access anti-corruption layer; document extraction and
organization control-plane responsibilities remain external contracts.

## Authoritative artifacts

| Concern | Current evidence | Status |
| --- | --- | --- |
| PRD | `docs/prd-integrated-document-viewer-platform.md` | Current |
| TRD | `docs/trd-integrated-document-viewer-platform.md` | Current |
| Architecture and Context Map | `ARCHITECTURE.md`, `docs/architecture.md` | Current |
| UML and interaction flows | `docs/diagrams/README.md` and bounded flow diagrams | Current |
| Authentication model | `docs/security/2026-07-02-auth-tenant-model.md` | Current |
| ERD | No canonical ERD is published in this repository | Gap |
| Change history | `CHANGELOG.md` | Current |

## Context Map

| Relationship | Contract boundary | Direction |
| --- | --- | --- |
| Identity provider to Clearfolio | Tenant claims and explicit permissions | Upstream to ACL |
| Conversion storage to Clearfolio | Repository interfaces and artifact identifiers | Upstream to ACL |
| Clearfolio to browser | Versioned HTTP responses and signed artifact links | Product API |
| Organization CI to Clearfolio | Reusable security and review workflows | Conformance only |

## Gap and action register

| Gap | Exact evidence | Action | Status |
| --- | --- | --- | --- |
| Administrator endpoints lacked an explicit operation permission | PR #659 source and tests | Require `ADMIN_OPERATE` through `TenantAccessService` | Implemented; exact-head acceptance pending |
| Jackson 2.22.1 is affected by five September 2026 advisories | Security run `36792153106`, job `110147365860` | Pin Jackson BOM and databind to 2.22.3 and guard the POM version | Implemented; Security Scan green at `7b90d4f7…` |
| Canonical ERD is absent | Repository documentation inventory at the evidence head | Publish the persisted conversion-job and tenant ownership model without inventing storage not present in code | Proposed |
| Draft suppresses required current-head CodeQL evidence | CodeQL run `36794116094` is skipped | Complete ordinary checks, restore Ready, and require fresh CodeQL plus independent review | Pending |

## Acceptance rule

A row becomes complete only when its implementation, regression contract, documentation, and
exact-head hosted checks are green. Draft-gated, queued, skipped, stale-head, or predecessor-head
results are not acceptance evidence. This baseline must be updated whenever the PRD, TRD,
Context Map, persistence model, or a listed Gap changes.
