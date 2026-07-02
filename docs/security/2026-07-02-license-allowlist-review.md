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
| Engineering policy | `docs/security/2026-07-02-license-policy.json` |
| Policy checker | `scripts/check_sbom_license_policy.py` |
| Policy check evidence | `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/license-policy-summary.json` |

## Engineering Policy

| Class | Default decision | Buyer-readiness meaning |
| --- | --- | --- |
| Permissive licenses | Allow | Low diligence friction when attribution is preserved. |
| Weak copyleft or dual-license metadata | Review | Legal must confirm runtime use, distribution model, and obligations. |
| GPL, AGPL, or unclear strong copyleft metadata | Block until approved | Do not represent the package as buyer-ready without a legal decision or dependency removal. |
| Format-specific restrictive licenses | Review | Confirm whether current dependency usage triggers redistribution or feature restrictions. |
| Components without license metadata | Block until identified | No unknown-license component should enter a buyer release. |

Current SBOM status has 63 components, 7 unique license metadata entries, and
0 components without license metadata. The remaining buyer risk is not missing
metadata; it is whether the flagged transitive dependencies are acceptable under
Clearfolio's distribution and sale model.

The unused `tika-parsers-standard-package` dependency was removed after code
search showed no direct Tika API usage. This removed the review-required Tika
transitive dependencies `jhighlight`, `junrar`, and `juniversalchardet` from
the generated SBOM.

## Flagged Components

| Component | Version | License metadata | Current route |
| --- | --- | --- | --- |
| `logback-classic` | `1.5.18` | `EPL-1.0`, `GNU Lesser General Public License` | Review as standard logging dependency. |
| `logback-core` | `1.5.18` | `EPL-1.0`, `GNU Lesser General Public License` | Review as standard logging dependency. |
| `jakarta.annotation-api` | `2.1.1` | `EPL-2.0`, `GPL-2.0-with-classpath-exception` | Review classpath-exception scope. |

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
| Flagged components with legal decision | 100 percent | 0 of 3 | Open |
| Disallowed copyleft runtime dependencies | 0 | Pending legal classification | Open |
| Automated allowlist enforcement | Required | Implemented for engineering review mode; buyer-release mode still waits on legal decisions | Partial |

This KPI belongs in the buyer diligence pack because unresolved license
questions can directly reduce acquisition value, slow legal review, or force a
late dependency replacement.

## Minimal Closure Plan

Ponytail decision: do not add a new license-scanning dependency in this PR. The
repo already has CycloneDX evidence, and the immediate value is a clear
allowlist decision path plus a small standard-library policy checker.

1. Treat this document as the current engineering allowlist review.
2. Keep `docs/security/2026-07-02-license-policy.json` aligned with legal
   decisions.
3. Run `scripts/check_sbom_license_policy.py` against each generated SBOM. It
   passes only when every component is either explicitly allowed or listed as a
   known review-required component.
4. If a component is rejected, remove or replace it before building a buyer
   data-room package.
5. For buyer-release mode, run the checker with `--require-no-review` so the
   three known review-required components fail until legal has approved or
   engineering has replaced them.

## Current Classification

| Diligence question | Status after this review |
| --- | --- |
| Can a buyer see all dependency license metadata? | Yes. |
| Can Clearfolio claim license clearance? | No. Legal decisions are still open. |
| Is there automated drift detection? | Yes. The policy checker reports 60 allowed components, 3 review-required components, and 0 unlisted violations for the current SBOM. |
| Is there an actionable next step? | Yes. Three flagged components need approve, replace, or remove decisions, then buyer-release mode can require zero review-required components. |
| Is a repository split or submodule needed for license closure? | No. The risk is dependency policy, not code ownership. |
