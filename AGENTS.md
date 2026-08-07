# AGENTS Operating Guide

## Purpose

This file defines repository-level operating assumptions for automated agents,
including mandatory quality and security merge gates.

## Mandatory merge gates

- `mvn -B --no-transfer-progress verify` is the authoritative local and CI
  acceptance command. Do not substitute `compile`, `test`, or a predecessor
  head result for this exact-head lifecycle.
- Java 21 compilation must pass with warning and deprecation budget = 0.
- Every test must pass with zero failures, errors, and skips.
- JaCoCo coverage for the `com.clearfolio.viewer.*` production package must
  remain 100% statement/line and branch coverage, expressed as zero missed
  production lines and branches.
- The verify lifecycle must generate public Javadocs with Maven Javadoc Plugin
  3.12.0, `doclint=all`, `failOnError=true`, and `failOnWarnings=true`. Public
  record components, constructors, methods, enum values, fields, parameters,
  return values, and thrown failures must be understandable without reading the
  implementation.
- Markdown lint for changed docs must pass.
- Security evidence must be attached on PR (SAST/code-scanning checks).
- CodeQL Java/Kotlin analysis must remain enabled through repository default
  setup or an org-central workflow; do not add a repo-local advanced CodeQL
  workflow while default setup is enabled because GitHub rejects duplicate
  advanced/default SARIF processing.
- Dependabot must remain enabled for Maven and GitHub Actions manifests.
- Fuzzing coverage for security-sensitive parsing/header paths must remain
  discoverable through Jazzer or ClusterFuzzLite-compatible targets.
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
- `mvn verify` includes `DependencyPolicyTest`, which prevents reintroducing the
  broad `tika-parsers-standard-package`, default Logback starter, excluded
  Jakarta annotation dependency, an unreviewed Netty version, or a weakened
  public-Javadoc gate unless a future PR updates the corresponding security,
  license, SBOM, attribution, acceptance, and buyer-diligence evidence together.
- CI, Security Scan, SAST Semgrep, every fuzz target, required organization
  reviews, and branch protection must all pass on the exact current PR head.
  Queued, pending, cancelled, skipped-required, stale-head, or predecessor-head
  evidence is not passing.

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
    version) in `pom.xml`; re-run `mvn -B --no-transfer-progress verify`.
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

### Config & secrets (KV, not env)

- Org rule: do **not** read runtime config/secrets via `os.getenv()` / raw
  environment variables (or the Spring `${ENV_VAR:...}` equivalent) as the
  runtime source. Read them from a KV / credential registry. Org Actions
  secrets (e.g. `OPENAI_API_KEY`) flow **into** the KV via a bootstrap/CI step;
  runtime reads from the KV — env is only transport into the KV, never the
  runtime source.
- Reference implementation: xtrmLLMBatchPython's pgcrypto-encrypted Postgres
  credential registry (`get_credential(name)`). Reuse that pattern (a DB-backed
  KV is fine) unless a dedicated KV is adopted.
- **This repo applies** — it is a Spring Boot service with real runtime secrets.
  Tenant-claim and artifact-token HMAC secrets are loaded respectively as
  `clearfolio.tenant-claims.hmac-secret` and
  `clearfolio.artifact-token.secret` from the Spring config-tree credential
  mount selected by the non-secret `CLEARFOLIO_SECRET_CONFIG_DIR` bootstrap
  setting. Do not restore direct runtime environment binding for either key.
  New secrets and credentials must enter through the mounted credential source,
  not new process-environment reads.

### Durable cleanup boundary

- This slice integrates a single-process, receipt-first cleanup worker. It
  forces deletion intent before the first artifact read, binds an exact digest
  before metadata tombstoning, retries incomplete cleanup after startup and on
  a bounded fixed-delay schedule, and exposes only low-cardinality aggregate
  evidence. The filesystem-backed receipt ledger is restart-replayable; the
  in-memory adapter remains available for standalone tests and ephemeral use.
- Do not overstate that reference boundary. It does not provide a cross-resource
  transactional outbox, a distributed generation fence, or remote-object-store
  atomicity across multiple service instances. Production adapters must provide
  equivalent durable uniqueness, object-version preconditions, idempotent
  retries, and operator recovery evidence before distributed cutover.
- Issue #263 remains the umbrella for the distributed adapter and later truthful
  API/UI lifecycle work. Do not describe durable cleanup as absent, and do not
  describe the current single-process implementation as a distributed deletion
  transaction.

### Code exploration

- There is no `.codegraph/` index in this repo, so use normal search (grep /
  find / IDE, `mvn dependency:tree` for the dependency graph). If a `.codegraph/`
  index is later added at the repo root, prefer CodeGraph
  (`codegraph explore "<query>"` or the code-review-graph MCP tools) BEFORE
  grep/find — it surfaces callers/callees/impact that text search misses.

### This repo's role in the ecosystem

- **This repo's role: Document viewer.** clearfolio renders uploaded
  documents/artifacts for the rest of the org.
- The org is an ecosystem around **naruon** — the hub: an email/PIM that
  DOM-decomposes emails and files into a persisted knowledge graph. Each
  component is a standalone program that must ALSO work as a git submodule of
  the hub, grown separately and together.
- Sibling components: **wardnet** (WAF / IDS / AI SOC / LB / APIM),
  **pg-erd-cloud** (ERD tool), **contextual-orchestrator** (LLM
  cost/perf/upstream-LB gateway, beyond LiteLLM), **codec-carver** (STT /
  omni-modal speech-video codec), **fast-mlsirm** (LLM-as-a-Judge calibration +
  evaluation-item quality, using aFIPC FIPC + kaefa item-fit), **keyverse**
  (passwordless SSO — OIDC/SCIM/ADFS/LDAP/FIDO2/OAuth2.1, eliminate passwords),
  **newsdom-api** (PDF→DOM sidecar), and **semantic-data-portal** (upper
  ontology / catalog / governance plane with its own graph engine).

### Research grounding (attach paper PDFs)

- Org rule: substantive feature/process PRs should find the relevant academic
  papers and **commit their PDFs into the PR** (e.g. a `docs/papers/` or
  `references/` dir) with full citations. Respect copyright — attach the PDF only
  when redistribution is permissible; otherwise cite + link + a short summary in
  place of the file.
- For clearfolio (a document viewer), ground viewer/rendering work in the
  relevant literature — e.g. document layout analysis, PDF/DOM structure
  extraction, and accessible/large-document rendering — and cite it in the PR.
<!-- END cwl-agent-guidance -->
