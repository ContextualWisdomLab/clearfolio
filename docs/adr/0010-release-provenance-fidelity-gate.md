# ADR-0010: Gate release on integrated provenance, fidelity, accessibility and operability evidence

Status: Accepted
Implementation maturity: `PARTIAL`

## Context and drivers

A green unit-test run or a generated SBOM does not establish that a document-conversion product is releasable. Clearfolio handles untrusted customer documents, tenant boundaries, generated artifacts and asynchronous recovery. Release therefore needs evidence across correctness, security, fidelity, accessibility, supply chain and operations on one integrated protected revision.

## Alternatives

1. Release whenever the feature PR's CI is green.
2. Release from a manually selected commit after local verification.
3. Release only from an exact integrated protected head after the full acceptance graph passes.

## Decision

Choose alternative 3. `docs/RELEASE_ACCEPTANCE.md` is the canonical complete release checklist; this ADR defines why that checklist is authoritative rather than maintaining a weaker parallel list. Release acceptance includes required CI/security/SAST/fuzz, exact owned coverage/docstrings, realistic document-fidelity acceptance for every claimed format, accessibility, package/runtime compatibility, SBOM/third-party attribution/provenance, reproducibility, API/schema compatibility, migration/rollback/recovery where applicable, independent review according to governance, and protected-main operational evidence.

At minimum the exact integrated candidate must demonstrate zero skips, failures, and errors; zero warnings and deprecations; warning-free public Javadocs/doclint; applicable JavaScript coverage for owned executable browser code; Markdown lint; and live required review and security gates. These are necessary release-evidence classes, not substitutes for realistic fidelity, security, recovery, accessibility or operational assertions.

## Consequences

Release cadence may be slower than feature cadence. Evidence is easier for customers and acquirers to audit, and unsupported format claims cannot slip into release notes merely because placeholder rendering exists.

## Failure and recovery

Any required failed, pending, cancelled, skipped-required, absent or stale evidence blocks release. A release failure triggers correction on a new candidate revision; predecessor evidence does not silently transfer. Published artifacts with a discovered defect are superseded using the repository's release/incident process rather than rewriting history.

## Security and privacy

Provenance and evidence artifacts must not leak source documents, raw tenant identifiers, secrets, approval tokens, internal paths or uncontrolled exception data. Supply-chain metadata is public/shareable only after disclosure review appropriate to the artifact.

## Compatibility and migration

Version bumps and schema changes require explicit compatibility notes. Stateful migrations require tested forward/rollback or recovery paths before release. Standalone and supported MSA integration profiles must be validated independently when both are claimed.

## Tests and acceptance

The executable acceptance source remains `docs/RELEASE_ACCEPTANCE.md`. This ADR requires that source to preserve, when applicable:

- exact integrated source identity;
- zero skips, failures, and errors in required test evidence;
- zero warnings and deprecations in owned production compilation;
- warning-free public Javadocs/doclint;
- exact owned Java line/branch coverage and applicable JavaScript coverage;
- Markdown lint and documentation-contract validation;
- realistic format fixtures and fidelity thresholds;
- accessibility acceptance;
- SBOM/attribution deterministic regeneration;
- provenance/reproducible package checks;
- migration/rollback/restart acceptance when state changes;
- live required review and security gates, including independent review and protected-main operational verification.

## Rollback / supersession

Rollback uses a previously accepted artifact/version only when data/schema compatibility permits. Supersede this ADR only with an equal or stronger evidence model documented with migration and customer-impact handling.
