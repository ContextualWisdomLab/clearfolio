# KRW 2B Sale-Readiness Evidence

Date: 2026-07-02
Verification source head SHA before this evidence refresh:
`a088d8e0ec07a6585b263c3b953ce11b433e6933`

## Gate Summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Compile warnings/deprecations | Pass | `compile.log` |
| Tests + JaCoCo | Pass, `classes=46`, `line_missed=0`, `branch_missed=0` | `test-jacoco.log`, `jacoco.csv`, `jacoco-status.txt` |
| JavaDoc | Pass, `javadoc_warnings_or_errors=none` | `javadoc.log`, `javadoc-status.txt` |
| Markdown lint | Pass, 0 errors across changed docs | `markdownlint.log` |
| JS syntax | Pass | `node-check.log` |
| SAST | Pass, 0 findings | `semgrep.log`, `semgrep.json` |
| SBOM | Pass, CycloneDX 1.6, 142 components, 0 components without license metadata | `sbom-cyclonedx.log`, `sbom-cyclonedx.json`, `sbom-status.txt` |
| License review | Partial, 6 flagged components need legal decision | `docs/security/2026-07-02-license-allowlist-review.md` |
| Auth/tenant and signed artifacts | Partial, runtime tenant enforcement, signed artifact tokens, token revocation, and artifact read audit API implemented; OIDC/JWT, durable revocation, and persisted audit pending | `docs/security/2026-07-02-auth-tenant-model.md`, `docs/security/2026-07-02-signed-artifact-link-design.md`, auth/artifact tests |
| Local smoke | Pass | `smoke-local.txt` |
| GitHub PR state | Queued checks; review required | `gh-pr-state.json`, `gh-pr-checks.txt` |

## SAST

Command:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Result:

- Semgrep completed successfully.
- Rules run: 60 Java rules.
- Targets scanned: 47 tracked files.
- Findings: 0.
- Errors: 0.

Evidence:

- `semgrep.json`

## SBOM

Command:

```bash
mvn -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -Dcyclonedx.skipAttach=true -Dcyclonedx.outputFormat=json -Dcyclonedx.outputName=clearfolio-viewer-sbom
```

Result:

- CycloneDX BOM format: 1.6.
- Components: 142.
- Components without license metadata: 0.
- Unique license metadata entries: 20.
- Engineering license review is now documented in
  `docs/security/2026-07-02-license-allowlist-review.md`.
- License clearance remains open because 6 flagged components need legal
  approve, replace, or remove decisions before buyer use.

Evidence:

- `sbom-cyclonedx.log`
- `sbom-cyclonedx.json`
- `sbom-status.txt`
- `docs/security/2026-07-02-license-allowlist-review.md`
- `docs/security/2026-07-02-auth-tenant-model.md`

## Local Smoke

Command path:

- Start app on `localhost:18080`.
- Verify `GET /`, `/assets/viewer/demo.js`, missing-auth KPI denial,
  authenticated empty KPI snapshot, document upload with tenant headers, status
  polling to `SUCCEEDED`, `/viewer/{docId}`, authenticated viewer bootstrap,
  signed artifact URL creation, unsigned artifact denial, signed artifact range
  access, artifact read audit lookup, artifact token revocation, revoked-token
  denial, cross-tenant status denial, and post-upload KPI snapshot.

Result:

- Root shell: 200.
- Demo JS: 200.
- Missing-auth KPI: 401.
- Authenticated empty KPI: 200.
- Final conversion status: `SUCCEEDED`.
- Status tenant: `buyer-demo`.
- Viewer HTML: 200.
- Viewer bootstrap: 200.
- Artifact link creation: 200.
- Unsigned artifact read: 401.
- Signed artifact range read: 206.
- Artifact read audit lookup: 200, 1 event, last status 206.
- Artifact token revocation: 200, `revoked=true`.
- Revoked artifact read: 403.
- Cross-tenant status lookup: 404.
- Post-upload KPI: `totalJobs=1`, `succeededJobs=1`,
  `conversionSuccessRate=1.0`, numeric `p95TimeToPreviewMs`.

Evidence:

- `smoke-local.txt`

## GitHub Checks

PR checks visible to this run:

- `coverage-evidence`: queued.
- `scan-pr-queue`: queued.
- `strix`: queued.

Review and queued checks are not treated as a blocker for continuing the
sale-readiness work.
