# AGENTS Operating Guide

## Purpose

This file defines repository-level operating assumptions for automated agents,
including mandatory quality and security merge gates.

## Mandatory merge gates

- `mvn -DskipTests compile` must pass with warning/deprecated budget = 0.
- `mvn test` must pass.
- JaCoCo coverage for production package must remain 100% line/branch.
- JavaDoc gate must pass (`mvn -q -DskipTests javadoc:javadoc`) with no warnings/errors.
- Markdown lint for changed docs must pass.
- Security evidence must be attached on PR (SAST/code-scanning checks).
- License policy drift check must pass in engineering-review mode:
  `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json`.
  Buyer-release evidence must also pass with `--require-no-review`.
- Third-party attribution drift check must pass:
  `python3 scripts/render_third_party_attribution.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --output docs/legal/2026-07-03-third-party-attribution.md --check`.
- Buyer data-room manifest check must pass:
  `python3 scripts/check_buyer_dataroom_manifest.py --manifest docs/diligence/2026-07-03-buyer-data-room-manifest.json`.
- Buyer readiness scorecard drift check must pass:
  `python3 scripts/summarize_buyer_readiness.py --manifest docs/diligence/2026-07-03-buyer-data-room-manifest.json --output docs/diligence/2026-07-03-buyer-readiness-scorecard.md --summary docs/qa/evidence/2026-07-02-krw2b-sale-readiness/buyer-readiness-scorecard-summary.json --check`.
- Figma Slides generation payload check must pass:
  `python3 scripts/check_figma_deck_payload.py --payload docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json --summary docs/qa/evidence/2026-07-02-krw2b-sale-readiness/figma-deck-payload-check.json`.
- `mvn test` includes `DependencyPolicyTest`, which prevents reintroducing the
  broad `tika-parsers-standard-package`, default Logback starter, or excluded
  Jakarta annotation dependency unless a future PR updates the license policy,
  SBOM evidence, attribution package, and buyer diligence docs together.

## Change management rule

When a new gate is added (license-scan, security-scan, queue policy, etc.),
this file must be updated in the same PR so reviewers and operators have a
single source of truth.
