# GitHub Transfer Metadata

Last updated: 2026-08-09

This file captures repository handoff and acquisition-readiness details that must
be reproducible from GitHub without conversation history. It describes the
current protected-main repository plus explicitly identified active work; it
does not promote an active pull request to shipped behavior.

## Identity

- Repository: `ContextualWisdomLab/clearfolio`.
- Product intent: secure integrated document conversion and viewing.
- Language/runtime: Java 21 / Spring Boot / Maven.
- Primary package: `com.clearfolio.viewer`.
- Maven artifact: `clearfolio-viewer`.
- License: Apache License 2.0 in root `LICENSE`.
- Protected default branch: `main`.

## Authoritative documentation graph

PR #305 introduces the canonical product/architecture spine. Until that PR
integrates, protected `main` still contains historical documentation that may be
stale. The review target is:

- `docs/PRD.md`;
- `docs/TRD.md`;
- root `ARCHITECTURE.md`;
- `docs/DATA_MODEL.md`;
- `docs/UML.md`;
- `docs/API_CONTRACT.md`;
- `docs/THREAT_MODEL.md`;
- `docs/TEST_STRATEGY.md`;
- `docs/OPERABILITY.md`;
- `docs/TRACEABILITY.md`;
- `docs/RESEARCH_TRACEABILITY.md`;
- `docs/FIDELITY_ACCEPTANCE.md`;
- `docs/MIGRATION_ROLLBACK.md`;
- `docs/RELEASE_ACCEPTANCE.md`;
- `docs/adr/README.md` and detailed ADRs.

## Transfer and acquisition-readiness checklist

- [x] Root README exists and points to current product boundaries.
- [x] Apache-2.0 root `LICENSE` exists.
- [x] CI workflow exists and enforces exact-head tests, zero-missed owned
  coverage, Javadocs, no-skipped-test evidence, and merge compatibility.
- [x] Security Scan, SAST Semgrep, and fuzz workflows exist.
- [x] CodeRabbit review/status integration is active.
- [x] SBOM, third-party attribution, security, fidelity, migration/rollback, and
  release-acceptance authorities are represented in repository evidence or the
  canonical documentation PR.
- [x] PRD, TRD, Architecture, ADR, conceptual ERD/data model, UML, API contract,
  threat model, test strategy, operability, and traceability are consolidated by
  PR #305 and guarded by `scripts/test_documentation_spine_contract.py`.
- [ ] Explicit `CODEOWNERS` for the eventual transfer team. No transfer-team
  identity is invented here; configure this only when an eligible team/user is
  actually known and accepted by repository governance.
- [ ] Qualifying independent non-author review route demonstrated for the final
  transfer/release head. Automated review/status evidence does not substitute
  for a counted independent approval when policy requires one.
- [ ] Production Office transformed-format conversion qualified. Protected
  `main` currently has PDF passthrough plus non-PDF development/demo placeholder
  generation; issue #5 owns the sandboxed provider-neutral Office adapter and
  fidelity qualification gap.
- [ ] Production identity and distributed durable-state integrations completed
  or contractually assigned to the receiving platform; current limitations must
  remain explicit in PRD/TRD/Architecture/release evidence.
- [ ] One exact protected release head has complete CI, security, independent
  review, fidelity, recovery, SBOM/provenance, packaging, rollback and
  operational-acceptance evidence before transfer is described as release-ready.

## Handover pointers

- Core implementation: `src/main/java/com/clearfolio/viewer`.
- Public HTTP/API entry points: `src/main/java/com/clearfolio/viewer/controller`.
- Tests: `src/test/java/com/clearfolio/viewer` and repository contract tests
  under `scripts/`.
- Security authority: root `SECURITY.md` plus `docs/security/` and
  `docs/THREAT_MODEL.md` in the canonical spine.
- Operations: `docs/OPERABILITY.md` plus `docs/operations/`.
- Release/fidelity authority: `docs/RELEASE_ACCEPTANCE.md` and
  `docs/FIDELITY_ACCEPTANCE.md` in the canonical spine.
- Product gaps: issue #5 for Office conversion and issue #263 for the remaining
  tenant-safe user lifecycle after active substrate PRs integrate.

## Evidence rule

A checked repository capability means the repository contains the named source
or evidence boundary; it does not mean the software is commercially complete.
Active-PR behavior remains `ACTIVE_PR`, planned architecture remains `PLANNED` or
`ACCEPTED_ARCHITECTURE`, and only protected-main code may be described as
`IMPLEMENTED_ON_MAIN`.
