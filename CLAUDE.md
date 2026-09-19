# CLAUDE.md

This file provides repository-specific guidance for Claude Code and other assistants that read it.

## Authority order

Read `AGENTS.md` first. It defines mandatory merge, security, evidence, documentation and autonomous-writer rules. Then use the canonical documentation spine:

- `docs/PRD.md`
- `docs/TRD.md`
- `ARCHITECTURE.md`
- `SECURITY.md`
- `docs/adr/README.md`
- `docs/DATA_MODEL.md`
- `docs/UML.md`
- `docs/API_CONTRACT.md`
- `docs/THREAT_MODEL.md`
- `docs/TEST_STRATEGY.md`
- `docs/OPERABILITY.md`
- `docs/TRACEABILITY.md`
- `docs/DOCUMENTATION_ASSESSMENT.md`
- `docs/engineering/acceptance-criteria.md`

Supplemental release and research authorities:

- `docs/FIDELITY_ACCEPTANCE.md`
- `docs/MIGRATION_ROLLBACK.md`
- `docs/RELEASE_ACCEPTANCE.md`
- `docs/RESEARCH_TRACEABILITY.md`

Historical plans, evidence snapshots, PR bodies, `.jules/` notes and old MVP PRD/TRD documents are supporting provenance, not current source of truth. Never infer live head/base/check/review state from them.

## Canonical verification

Toolchain: Java 21, Maven, Python 3 for repository helper scripts, and Node where viewer JavaScript tests require it.

```bash
# Full Maven acceptance: compile/tests/coverage/Javadocs and configured verification gates
mvn -B --no-transfer-progress verify

# Fail closed if Maven XML evidence is missing, malformed, empty, skipped or failing
python3 scripts/verify_maven_test_reports.py

# Repository helper and documentation contract tests
python3 -m unittest discover -s scripts

# Focused test examples during TDD
mvn test -Dtest=ConversionControllerTest
mvn test -Dtest=ConversionControllerTest#methodName
```

Local app smoke uses two terminals so the foreground Spring process remains running:

```bash
# Terminal 1
mvn spring-boot:run
```

```bash
# Terminal 2, after Terminal 1 reports the application started
curl -fsS http://localhost:8080/healthz
```

Focused tests accelerate RED/GREEN iteration but never replace final `mvn verify` and exact-head GitHub evidence.

## What Clearfolio is now

Clearfolio is a secure document-conversion and viewing service, independently deployable and composable with naruon and other CWL hosts through explicit versioned contracts. The runtime is Spring WebFlux. Heavy conversion work remains outside request completion. The service owns document validation/conversion contracts, conversion lifecycle interfaces, local tenant authorization enforcement, signed artifact-delivery semantics, artifact/state adapters, and product-specific evidence.

The default conversion-job repository on protected main remains process-local. `FileSystemArtifactStore` provides configurable local artifact persistence; selected append-only/file-backed ledgers provide only their documented evidence durability. Do not describe those as a durable distributed job system.

Entry point: `src/main/java/com/clearfolio/viewer/ClearfolioViewerApplication.java`.

## Current runtime map

Production code lives under `src/main/java/com/clearfolio/viewer/`.

- `controller/` — HTTP ingress, viewer/artifact/admin/analytics/availability surfaces and exception mapping.
- `auth/` — tenant/subject/permission claims and same-tenant enforcement.
- `service/` — validation, conversion orchestration, worker/retry/recovery and lifecycle coordination.
- `repository/` — job read/dedupe and lifecycle-state interfaces; current default implementation is in memory.
- `artifact/` — artifact stores, placeholder generator, signed artifact link/token/ledger logic and artifact evidence.
- `model/` — conversion lifecycle and immutable business state.
- `api/` — request/response records.
- `analytics/` — KPI/evidence surfaces.
- `config/` — bounded executor, artifact-store, security and runtime configuration.
- `exception/` — controlled domain failures.

Static viewer assets and PDF.js integration live under `src/main/resources/static/assets/viewer/`.

## Conversion truth

Protected main supports validated PDF passthrough and PDF viewing. `PdfBoxArtifactGenerator`'s non-PDF one-page PDF is a development/demo placeholder, **not a faithful DOCX/HWP/Office converter**. Never expand supported-format claims without a deterministic converter plus authorized realistic fixture-based fidelity/security/accessibility evidence.

Uploaded documents and every document-derived filename/string are untrusted. Production conversion must not grant macro/script execution or implicit external network access. Unsupported or unsafe input fails closed.

## Tenant and artifact authority

Cross-tenant resources are concealed according to server-side policy. Metadata permission and document-byte authority are not interchangeable.

The canonical artifact path uses `ArtifactLinkService` to bind signed reads to token id, tenant, subject, document, scope, purpose, artifact checksum, issue time, expiry, issuance ledger and revocation state; verified reads follow the controlled single-range/read-audit contract. A convenience download endpoint must not bypass that authority merely because the caller has `artifact:read`.

Signed tenant-claim, admin authorization, HMAC audit pseudonymization, immutable lifecycle-generation and deletion-recovery hardening may live on active PRs; check `docs/TRACEABILITY.md` and live GitHub before calling them protected-main functionality.

## Lifecycle

Public lifecycle values are currently:

- `SUBMITTED`
- `PROCESSING`
- `SUCCEEDED`
- `FAILED`

Retry exhaustion remains `FAILED` with dead-letter evidence rather than a separate public terminal enum. Do not invent lifecycle states in docs or clients without a versioned contract change.

## Availability

Protected main currently has its existing health surface. A separate `/readyz` traffic-readiness endpoint is active-PR work until integrated. Never describe active-PR availability semantics as shipped; always refetch the relevant PR/head.

## Exact evidence rules

Before reviewing, editing or merging:

1. refetch exact PR head;
2. independently resolve current base-branch tip;
3. refetch formal reviews and unresolved threads;
4. refetch exact-head checks/workflow results and actual security gates;
5. refetch target blob/ref before writing;
6. if any refetched target identity differs from the identity the write was prepared against, stop and freeze that write, preserve the fresh evidence, and rotate to a newly revalidated exact-head work item instead of writing against stale state.

PR-body SHAs and remembered run IDs are historical. Queued, pending, skipped-required, cancelled, absent, stale-head, predecessor-head, synthetic-only, model-only and failed results are not passing evidence. Commit statuses, automated-review comments and formal GitHub approvals are different authority classes.

Before merging, revalidate the protected `main` branch rules, **all required checks** and security gates, the required reviewer count, and the qualifying independent approval for the unchanged exact head. **Automated comments, check results, and model output do not count as approval.** If the source head or supported base tip moves, abort the stale merge attempt and restart from fresh evidence.

## TDD and coverage

Production source defects use strict RED → narrow root-cause implementation → GREEN → full relevant validation. A RED is valid only if it reaches the intended product boundary; a setup/import/fixture failure is not the desired regression.

Owned production line/branch coverage remains exactly 100%, and public production APIs require beginner-readable warning-free Javadocs. Coverage is not sufficient by itself: realistic security, lifecycle, concurrency, fidelity, accessibility, crash/restart and release assertions are required.

## Dependency and supply-chain changes

Dependency changes must update and verify security policy, license policy, SBOM/third-party attribution and buyer-diligence evidence as applicable. Do not add a duplicate repo-local advanced CodeQL workflow while the repository/organization's current CodeQL authority is already active. Keep GitHub Actions sources immutably pinned where practical.

## Standalone and MSA composition

Clearfolio remains independently usable. naruon or another host may own higher-level user workflow, upstream identity/federation and deployment composition, but communicates through explicit APIs/contracts and does not write Clearfolio application persistence directly.

`contextual-orchestrator` is optional for explicitly model-backed features and is not authority for deterministic conversion, authorization, fidelity or release acceptance.

## Autonomous development

The dedicated Clearfolio commercial loop is the writer automation while enabled. Central `.github` owns privileged PR-maintenance/review/merge control-plane behavior; local product development must not duplicate those credentials or authorities.

Model-backed autonomous development uses an immutably pinned OpenCode Agent and GitHub Secret `NVIDIA_NIM_API_KEY`, never `COPILOT_GITHUB_TOKEN` as a development-model credential. Model output is an untrusted bounded proposal. It cannot self-approve, merge, release or deploy.

A blocker, one commit, one documentation update, one green test, a pending review or an external-approval wait is intermediate while safe work remains. Use `exact evidence → RCA → distinct remedies → empirical feasibility → test-first action → proof → next queue item`. Branch conflicts are branch-local. Normal no-work termination requires two fresh repository-wide exit sweeps.

## Documentation change rule

A material change to product ownership, public API/lifecycle, tenant/security authority, logical/persistent entities, conversion support/fidelity, automation authority, recovery or release acceptance updates the corresponding canonical PRD/TRD/Architecture/ADR/UML/ERD/API/Threat/Test/Operability/Traceability documents in the same reviewed change or explicitly proves no impact.

Supplemental release and research authorities must also remain synchronized when applicable: `docs/FIDELITY_ACCEPTANCE.md`, `docs/MIGRATION_ROLLBACK.md`, `docs/RELEASE_ACCEPTANCE.md`, and `docs/RESEARCH_TRACEABILITY.md`.

`python3 -m unittest discover -s scripts` must keep the canonical documentation contract executable.

## Research and standards

Use current authoritative standards, primary technical documentation and peer-reviewed primary research for material security, document-processing, accessibility, testing, provenance, interoperability and governance decisions. Record full APA 7 references with stable links. Commit a paper PDF only when redistribution is legally permitted; otherwise cite/link/summarize it without copying the PDF.
