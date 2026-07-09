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
- CodeQL Java/Kotlin analysis must run for pull requests and `main`.
- Dependabot must remain enabled for Maven and GitHub Actions manifests.
- Fuzzing coverage for security-sensitive parsing/header paths must remain
  discoverable through Jazzer or ClusterFuzzLite-compatible targets.
- License policy drift check must pass in engineering-review mode:
  `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json`.
  Buyer-release mode adds `--require-no-review` after legal approval or
  dependency replacement clears all review-required components.

## Change management rule

When a new gate is added (license-scan, security-scan, queue policy, etc.),
this file must be updated in the same PR so reviewers and operators have a
single source of truth.
