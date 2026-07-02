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

Current SBOM status has 61 components, 3 unique license metadata entries, and
0 components without license metadata. The current buyer-release policy has no
known review-required components; remaining license work is ordinary attribution
and final legal review of the release package, not an unresolved dependency
exception.

## Flagged Components

No current SBOM component is classified as review-required by
`docs/security/2026-07-02-license-policy.json`.

Removed before buyer-release mode:

- `tika-parsers-standard-package` was removed because current production code
  does not use Tika APIs.
- This removes Tika transitive review-required components `jhighlight`,
  `junrar`, and `juniversalchardet` from the current SBOM.
- Spring Boot's default Logback logging starter was replaced with
  `spring-boot-starter-log4j2`, removing `logback-classic` and `logback-core`
  from the current SBOM.
- Spring Boot starter declarations now exclude `jakarta.annotation-api`, which
  removes the remaining GPL classpath-exception metadata item from the current
  SBOM.
- `DependencyPolicyTest` prevents reintroducing the broad Tika parser package
  and the default Logback/Jakarta annotation paths without an explicit policy
  and diligence update.

## Buyer Diligence Position

The repository is now SBOM-visible and passes the engineering buyer-release
license policy with `--require-no-review`. A buyer can inspect the generated
SBOM, the policy file, and the policy-check evidence without seeing a known
review-required dependency exception.

Before a signed sale or enterprise redistribution, legal should still review the
final attribution package and distribution model. That is a normal release
approval step, not a known dependency replacement blocker in the current SBOM.

## KRW 2B Sale-Readiness KPI

| KPI | Target | Current value | Status |
| --- | --- | --- | --- |
| Components with license metadata | 100 percent | 100 percent | Ready |
| Flagged components with legal decision | 100 percent | 0 open flagged components | Ready |
| Disallowed copyleft runtime dependencies | 0 | 0 known policy violations | Ready |
| Automated allowlist enforcement | Required | Buyer-release mode passes with `--require-no-review` | Ready |

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
5. For buyer-release mode, run the checker with `--require-no-review`; any
   future review-required component will fail until legal approves it or
   engineering replaces it.

## Current Classification

| Diligence question | Status after this review |
| --- | --- |
| Can a buyer see all dependency license metadata? | Yes. |
| Can Clearfolio claim engineering buyer-release license-policy clearance? | Yes. The current SBOM passes with zero review-required components and zero violations. |
| Is there automated drift detection? | Yes. The policy checker reports 61 allowed components, 0 review-required components, and 0 unlisted violations for the current SBOM. |
| Is there an actionable next step? | Yes. Keep the buyer-release policy gate in CI evidence and prepare final attribution/legal review for the buyer data room. |
| Is a repository split or submodule needed for license closure? | No. The risk is dependency policy, not code ownership. |
