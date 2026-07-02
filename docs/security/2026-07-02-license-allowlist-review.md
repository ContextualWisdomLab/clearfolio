# License Allowlist Review

Date: 2026-07-02

This is an engineering review of the generated CycloneDX SBOM for buyer
diligence. It is not legal advice and does not replace legal approval before a
sale, enterprise license, or buyer data-room handoff.

## Source Evidence

| Evidence | Location |
| --- | --- |
| SBOM | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json` |
| SBOM status | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-status.txt` |
| SBOM generation log | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.log` |

## Engineering Policy

| Class | Default decision | Buyer-readiness meaning |
| --- | --- | --- |
| Permissive licenses | Allow | Low diligence friction when attribution is preserved. |
| Weak copyleft or dual-license metadata | Review | Legal must confirm runtime use, distribution model, and obligations. |
| GPL, AGPL, or unclear strong copyleft metadata | Block until approved | Do not represent the package as buyer-ready without a legal decision or dependency removal. |
| Format-specific restrictive licenses | Review | Confirm whether current dependency usage triggers redistribution or feature restrictions. |
| Components without license metadata | Block until identified | No unknown-license component should enter a buyer release. |

Current SBOM status has 142 components, 20 unique license metadata entries, and
0 components without license metadata. The remaining buyer risk is not missing
metadata; it is whether the flagged transitive dependencies are acceptable under
Clearfolio's distribution and sale model.

## Flagged Components

| Component | Version | License metadata | Current route |
| --- | --- | --- | --- |
| `logback-classic` | `1.5.18` | `EPL-1.0`, `GNU Lesser General Public License` | Review as standard logging dependency. |
| `logback-core` | `1.5.18` | `EPL-1.0`, `GNU Lesser General Public License` | Review as standard logging dependency. |
| `jakarta.annotation-api` | `2.1.1` | `EPL-2.0`, `GPL-2.0-with-classpath-exception` | Review classpath-exception scope. |
| `jhighlight` | `1.1.0` | `CDDL, v1.0`, `LGPL-2.1-or-later` | Review or remove if not needed for supported preview paths. |
| `junrar` | `7.5.5` | `UnRar License` | Review archive-format need; remove if unsupported formats do not require it. |
| `juniversalchardet` | `2.5.0` | `MPL-1.1`, `GPL-3.0`, `LGPL-3.0` | Review dual-license selection or replace before buyer release. |

## Buyer Diligence Position

The repository is now SBOM-visible but not license-cleared. A buyer can inspect
the generated SBOM and the flagged-component list, but the product should still
be described as `license review pending` until the following decisions are made:

1. Legal confirms the allowed license basis for each flagged component.
2. Engineering removes any component whose license route is not approved.
3. CI enforces the final allowlist for future dependency changes.

## KRW 2B Sale-Readiness KPI

| KPI | Target | Current value | Status |
| --- | --- | --- | --- |
| Components with license metadata | 100 percent | 100 percent | Ready |
| Flagged components with legal decision | 100 percent | 0 of 6 | Open |
| Disallowed copyleft runtime dependencies | 0 | Pending legal classification | Open |
| Automated allowlist enforcement | Required | Not implemented | Open |

This KPI belongs in the buyer diligence pack because unresolved license
questions can directly reduce acquisition value, slow legal review, or force a
late dependency replacement.

## Minimal Closure Plan

Ponytail decision: do not add a new license-scanning dependency in this PR. The
repo already has CycloneDX evidence, and the immediate value is a clear
allowlist decision path.

1. Treat this document as the current engineering allowlist review.
2. Add legal decisions beside the six flagged components.
3. Only after approval, add a simple CI gate that fails when a new license family
   appears outside the approved list.
4. If a component is rejected, remove or replace it before building a buyer
   data-room package.

## Current Classification

| Diligence question | Status after this review |
| --- | --- |
| Can a buyer see all dependency license metadata? | Yes. |
| Can Clearfolio claim license clearance? | No. Legal decisions are still open. |
| Is there an actionable next step? | Yes. Six flagged components need approve, replace, or remove decisions. |
| Is a repository split or submodule needed for license closure? | No. The risk is dependency policy, not code ownership. |
