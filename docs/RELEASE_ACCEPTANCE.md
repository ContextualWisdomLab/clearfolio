# Clearfolio Integrated Release Acceptance

Status: Canonical release gate
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

Clearfolio is releasable only from one exact integrated protected head. A green feature PR, documentation PR, local build, predecessor SHA, synthetic merge, or model/reviewer status is not a release candidate by itself.

## Release identity

Every candidate must bind evidence to:

- exact protected source commit SHA;
- exact version/tag to be created;
- immutable dependency lock/resolution state;
- build artifact digests;
- SBOM/provenance material generated from that source;
- converter/PDF/runtime versions and configuration affecting document output;
- supported deployment profile and configuration assumptions.

If the source head changes, source-sensitive acceptance evidence is stale and must be reacquired.

## Mandatory release gates

### Build and tests

- `mvn -B --no-transfer-progress verify` succeeds from the exact release head;
- Surefire evidence contains positive executed tests and zero skipped, failed, or errored tests;
- Failsafe evidence, when present, satisfies the same contract;
- owned production line and branch coverage gates remain at zero missed statements/branches as configured;
- public Javadocs/docstrings are generated without accepted warning debt;
- changed documentation contract tests pass.

### Product behavior

The release proves representative buyer workflows rather than only primitive units:

- submit → asynchronous status → terminal state;
- succeeded document → viewer bootstrap → signed artifact read;
- rejected/unsupported/failed document → controlled user/operator state;
- retry/dead-letter behavior where applicable;
- tenant-isolation and permission boundaries;
- viewer accessibility/keyboard behavior where changed;
- restart/recovery behavior for every durability claim in the release.

A product route that returns success with development/demo placeholder output cannot satisfy a production Office-fidelity release claim.

### Document fidelity

Every format advertised as transformed/supported must pass `docs/FIDELITY_ACCEPTANCE.md` on the exact release converter/runtime profile. PDF passthrough and development placeholder behavior must remain separately labelled.

If no Office adapter has qualified, the release notes and product documentation must say so plainly.

### Security and privacy

The exact release head must pass applicable repository security gates and preserve:

- signed tenant/claim validation where required;
- least-privilege permissions and cross-tenant concealment;
- signed artifact token integrity, expiry, scope, document/tenant/checksum binding, issued-ledger membership and revocation;
- canonical zero/single-range artifact delivery and controlled read audit;
- privacy-safe audit pseudonymization/key separation where present;
- blocked/unsupported active-content behavior and bounded conversion resources;
- no secrets or document contents in committed evidence/logging paths;
- immutable/pinned supply-chain automation according to repository policy.

A cancelled, skipped-required, absent-required, queued, stale-head, or predecessor security result is not passing evidence.

### Operability and recovery

- health/readiness behavior matches the implemented release rather than an active-PR target;
- configuration/secret requirements are documented and fail closed where required;
- rollback/recovery follows `docs/MIGRATION_ROLLBACK.md`;
- any durable-format or storage change has tested compatibility/restore evidence;
- representative startup, shutdown, failure, and recovery smoke tests pass for the declared deployment profile;
- monitoring/diagnostic behavior avoids secret/tenant/document leakage.

### Packaging and provenance

The release evidence must include or verify as applicable:

- reproducible or otherwise provenance-bound build inputs;
- dependency inventory/SBOM;
- third-party attribution/license policy evidence;
- artifact checksums;
- source/tag relationship;
- published package/container/artifact verification after publication.

Generated evidence is not automatically trustworthy because it exists; generation and verification must be tied to the exact release source and reviewed policy.

### Review and governance

- zero valid unresolved current-head human, CodeRabbit, GHAS, Dependabot, OpenCode, Noema, Strix, or equivalent findings that repository policy treats as blockers;
- all branch/ruleset required checks pass on the exact protected candidate;
- qualifying independent non-author formal approval is present when required by live repository/CWL governance;
- advisory comments, statuses, reactions, author reviews, dismissed reviews, predecessor reviews, and model-only verdicts are not counted as independent approval.

## Evidence authorities

Release evidence intentionally distinguishes:

- `source_head_sha` — exact protected source being released;
- `live_base_tip_sha` — protected branch tip used for integration decisions before release;
- workflow/check identity — execution and outcome on the source head;
- review identity — reviewer, review state and reviewed commit;
- security evidence — scanner/gate outcome on the exact source;
- product/fidelity evidence — realistic behavior on the release runtime profile;
- artifact evidence — hashes/SBOM/provenance for the built/published output.

No single green status collapses these authorities.

## Release sequence

1. drain/merge the intended PR stack through normal protection;
2. refetch the exact protected head and freeze the candidate identity;
3. run integrated CI, security, coverage/docstring, product, fidelity, packaging and recovery gates;
4. obtain required current-head independent review/governance evidence;
5. update version and `CHANGELOG.md` according to repository policy without changing accepted behavior unexpectedly;
6. rebuild/reverify from the final exact tagged head if versioning changed the source;
7. publish/create the release and its SBOM/provenance/checksums;
8. verify the published artifact against the expected source/digest;
9. run protected-main/released-artifact operational smoke/acceptance where the deployment profile supports it;
10. preserve dated release evidence without turning volatile SHA/run IDs into timeless architecture claims.

If any step changes source, dependencies, converter runtime, packaging or security-sensitive configuration, reacquire the affected evidence rather than reusing an older pass.

## Rollback triggers

Examples of release rollback/forward-fix triggers include:

- cross-tenant or artifact authorization regression;
- document corruption/data loss;
- deterministic conversion/fidelity regression beyond accepted tolerances;
- inability to replay required durable state;
- readiness/traffic-routing failure that makes safe serving impossible;
- package/provenance mismatch;
- newly confirmed high-impact vulnerability not safely mitigated by configuration.

Rollback must not reintroduce a known security bypass merely to restore compatibility.

## Traceability

- Product acceptance: `docs/PRD.md`
- Technical gates: `docs/TRD.md`
- Fidelity: `docs/FIDELITY_ACCEPTANCE.md`
- Migration/rollback/recovery: `docs/MIGRATION_ROLLBACK.md`
- Test strategy: `docs/TEST_STRATEGY.md`
- Operability: `docs/OPERABILITY.md`
- Threats: `docs/THREAT_MODEL.md`
- Release decision: `docs/adr/0010-release-provenance-fidelity-gate.md`
- Requirement/evidence mapping: `docs/TRACEABILITY.md`
