# Clearfolio Test Strategy

Status: Canonical quality strategy
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

## Goal

Tests must prove product behavior and release claims, not merely inflate coverage. Exact 100% owned production statement/branch coverage remains a floor; realistic security, lifecycle, fidelity, accessibility and recovery evidence supplies the behavioral meaning that coverage alone cannot provide.

## Test layers

### Unit and contract tests

Cover deterministic validation, lifecycle transitions, token parsing/signing, tenant permissions, audit pseudonymization, range parsing, filename/content boundaries, configuration and public API contracts.

### Integration tests

Exercise controller → service → repository/artifact boundaries with realistic request/response semantics, including tenant concealment, signed artifact delivery, worker state transitions and configured filesystem/ledger behavior.

### Security tests

Required classes include:

- missing/malformed/expired/revoked/wrong-scope/wrong-document artifact tokens;
- cross-tenant lookup/mutation concealment;
- weak/missing/shared cryptographic keys;
- hostile filenames, NUL/control characters, malformed Base64/UUID/epoch fields;
- oversized upload and parser resource limits;
- active-content/external-resource/macro boundaries when real Office conversion is introduced;
- privacy-safe logs and audit output;
- dependency/SAST/fuzz supply-chain gates.

### Concurrency and recovery tests

Cover duplicate submission races, bounded executor saturation, retry/dead-letter transitions, lifecycle serialization, stale processing recovery, crash/restart replay and—when #268 integrates—deletion receipt fairness, torn tails, generation mismatch and cleanup recovery.

### Document-fidelity tests

`PLANNED` and mandatory before advertising non-PDF source formats as supported. Use authorized realistic Office/PDF fixtures with expected:

- text and structure;
- page count/layout/pagination where deterministic enough to assert;
- fonts/styles/tables/images/links;
- extracted metadata where claimed;
- rendered artifact integrity;
- unsupported construct/degraded warning behavior;
- active-content/security outcome;
- print/accessibility outcome where claimed.

Fixture and expectation provenance must be reviewable. A synthetic-only mixture of trivial documents is insufficient for a commercial fidelity claim.

### Accessibility tests

Exercise accessible names for repeated actions, keyboard operation, busy-state semantics, focus restoration, ARIA restoration, no markup injection through document labels, error/status announcements, and print/export state as those surfaces mature.

### Packaging / provenance / release tests

Verify reproducible dependency resolution, deterministic SBOM/attribution regeneration where claimed, immutable workflow/action sourcing, package contents, startup smoke, supported runtime compatibility and exact release source identity.

## TDD rule

For a source/product defect:

1. write the smallest realistic regression that reaches the intended production boundary;
2. observe RED for the diagnosed reason, not a fixture/setup/import failure;
3. implement the narrowest root-cause fix;
4. observe GREEN on the focused regression;
5. run the relevant full suite and exact coverage/docstring gates;
6. publish and require exact-head GitHub evidence;
7. resolve only the review thread actually addressed.

If the first repair does not change the failing boundary, return to RCA rather than stack unrelated fixes.

## Coverage and test-report gate

Canonical Maven and script acceptance:

```bash
mvn -B --no-transfer-progress verify
python3 scripts/verify_maven_test_reports.py
python3 scripts/test_documentation_spine_contract.py
python3 -m unittest discover -s scripts
```

The direct documentation-contract command gives a focused failure surface when the canonical graph changes; standard-library discovery is the repository-wide canonical acceptance for `scripts/*.py` checks. Neither replaces Maven verification or Surefire/Failsafe report validation.

Required test-report properties:

- at least one executed Surefire test;
- zero skipped tests;
- zero failures and errors;
- explicit non-negative outcome counts;
- the same contract for Failsafe reports when present;
- bounded strict UTF-8 XML with DTD/entity/NUL rejection before parsing.

JaCoCo must report zero missed owned production lines and branches. Java public production APIs must pass warning-free Javadoc/doclint. JS production viewer utilities follow the repository's JS coverage gate when included in the production path.

## Exact-head evidence taxonomy

A local test or predecessor-head workflow result is diagnostic only. Merge evidence must bind the unchanged candidate head. Base-sensitive integration evidence is separate. Queued, pending, skipped-required, cancelled, absent, stale-head, predecessor-head, synthetic-only, model-only or failed evidence cannot be promoted to passing.

## Test data policy

- never commit confidential customer documents;
- prefer redistributable standards/examples or fixtures authored specifically for the repository;
- preserve fixture source/license/provenance;
- include hostile and edge-case fixtures without embedding live secrets or PII;
- size large fidelity assets deliberately and keep CI/runtime budgets bounded.

## Scientific / mathematical components

Clearfolio currently has no material psychometric numerical kernel. If one is introduced through future integration, production arithmetic follows the repository/CWL Rust-first CPU-multithreaded and parity-verified GPU policy where computationally material, with appropriate multilevel/multiple-membership and temporal structure rather than naive aggregation.

## References

Barr, E. T., Harman, M., McMinn, P., Shahbaz, M., & Yoo, S. (2015). The oracle problem in software testing: A survey. *IEEE Transactions on Software Engineering, 41*(5), 507–525. https://doi.org/10.1109/TSE.2014.2372785

Relevance: test execution is only useful when the oracle/evidence meaning is explicit; Clearfolio therefore validates report outcomes and domain assertions instead of treating process exit alone as acceptance.

Inozemtseva, L., & Holmes, R. (2014). Coverage is not strongly correlated with test suite effectiveness. In *Proceedings of the 36th International Conference on Software Engineering* (pp. 435–445). ACM. https://doi.org/10.1145/2568225.2568271

Relevance: exact coverage is retained as a structural gate, but realistic security/fidelity/recovery assertions are separately required because coverage percentage alone is not evidence of fault-detection effectiveness.
