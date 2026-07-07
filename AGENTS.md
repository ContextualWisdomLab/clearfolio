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
  Buyer-release mode adds `--require-no-review` after legal approval or
  dependency replacement clears all review-required components.

## Change management rule

When a new gate is added (license-scan, security-scan, queue policy, etc.),
this file must be updated in the same PR so reviewers and operators have a
single source of truth.

<!-- BEGIN cwl-agent-guidance -->
## Agent guidance (CWL governance)

Distilled ContextualWisdomLab org governance. Applies to ANY agent (Claude,
Codex, Cursor, opencode, …) working in this repo.

### Security scan gate

- Every PR runs a central, required **Security Scan** gate: `osv-scan` +
  `dependency-review` (diff-scoped) and `trivy-fs` (repo-wide, CRITICAL/HIGH,
  fixable). It runs on every PR base, **including stacked PRs**.
- A failing `trivy-fs` is a **REAL finding, not a flake.** Read the job log (it
  prints each finding's rule id / severity / file) or the run's SARIF results,
  then **remediate**:
  - This is a Maven / Spring Boot app — findings are almost always vulnerable
    Java dependencies. Fix by bumping the offending artifact (or its managed
    version) in `pom.xml`; re-run `mvn -DskipTests compile` and `mvn test`.
  - There is currently no `Dockerfile` or k8s manifest here; if one is added,
    trivy will also flag image/IaC misconfigs — fix those at the source.
  - For a genuine false positive only, add a narrow, **documented**
    `.trivyignore.yaml` entry (with the CVE id and a justification). Never
    weaken, broaden, or disable the gate to make it pass.
- Reproduce locally against the merge ref, not just the PR head, with a fresh
  DB: `trivy --download-db-only` then `trivy fs --severity CRITICAL,HIGH
  --ignore-unfixed .`. A stale local DB misses findings.
- The org `code_scanning` ruleset is intentionally **CodeQL-only** (multiple
  code-scanning tools can't converge on one PR ref). Gating is by the Security
  Scan **job result**, not the code_scanning rule — do not add tools to that rule.

### Code exploration

- There is no `.codegraph/` index in this repo, so use normal search (grep /
  find / IDE, `mvn dependency:tree` for the dependency graph). If a `.codegraph/`
  index is later added at the repo root, prefer CodeGraph
  (`codegraph explore "<query>"` or the code-review-graph MCP tools) BEFORE
  grep/find — it surfaces callers/callees/impact that text search misses.
<!-- END cwl-agent-guidance -->
