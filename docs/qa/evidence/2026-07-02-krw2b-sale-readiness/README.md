# KRW 2B Sale-Readiness Evidence

Date: 2026-07-02
Verification source head SHA before this evidence refresh:
`dc9366933cf14fbf27dcee342cb72d814e75579d`

## Gate Summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Java runtime | Pass, OpenJDK 21.0.11 | `java-version.txt` |
| Compile warnings/deprecations | Pass | `compile.log` |
| Tests + JaCoCo | Pass, 310 tests, `classes=48`, `line_missed=0`, `branch_missed=0` | `mvn-test.log`, `test-jacoco.log`, `jacoco.csv`, `jacoco-status.txt` |
| JavaDoc | Pass, `javadoc_warnings_or_errors=none` | `javadoc.log`, `javadoc-status.txt` |
| Markdown lint | Pass, 0 errors across changed docs | `markdownlint.log` |
| JS syntax | Pass | `node-check.log` |
| SAST | Pass, 0 findings | `semgrep.log`, `semgrep.json` |
| SBOM | Pass, CycloneDX 1.6, 142 components, 0 components without license metadata | `sbom-cyclonedx.log`, `sbom-cyclonedx.json`, `sbom-status.txt` |
| License review | Partial, policy checker reports 136 allowed components, 6 review-required components, 0 unlisted violations; legal decisions still needed | `docs/security/2026-07-02-license-allowlist-review.md`, `license-policy-summary.json`, `license-policy-test.log` |
| Auth/tenant, signed artifacts, and KPI snapshots | Partial, runtime tenant enforcement, optional gateway HMAC tenant-claim validation, signed artifact tokens, token revocation, artifact read audit API, optional file-backed artifact-link ledger replay, and optional file-backed KPI snapshot ledger replay implemented; OIDC/JWT and centralized durable revocation/audit/analytics persistence pending | `docs/security/2026-07-02-auth-tenant-model.md`, `docs/security/2026-07-02-signed-artifact-link-design.md`, auth/artifact/analytics tests |
| Local smoke | Pass, signed tenant claims plus file-backed artifact and KPI snapshot ledgers | `smoke-local.txt`, `smoke-app.log` |
| GitHub PR state | Queued checks; review required | `gh-pr-state.json`, `gh-pr-checks.txt` |

## SAST

Command:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Result:

- Semgrep completed successfully.
- Rules run: 60 Java rules.
- Targets scanned: 49 tracked files.
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
- The standard-library license policy checker passes engineering-review mode:
  136 allowed components, 6 review-required components, and 0 unlisted
  violations. Buyer-release mode should add `--require-no-review` after legal
  approval or dependency replacement.

Evidence:

- `sbom-cyclonedx.log`
- `sbom-cyclonedx.json`
- `sbom-status.txt`
- `license-policy.log`
- `license-policy-summary.json`
- `license-policy-test.log`
- `docs/security/2026-07-02-license-allowlist-review.md`
- `docs/security/2026-07-02-license-policy.json`
- `docs/security/2026-07-02-auth-tenant-model.md`
- FigJam diagrams:
  [Clearfolio Gateway Signed Tenant Claims Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM)
  and `Clearfolio KPI Snapshot Evidence Ledger Flow`.

## Local Smoke

Command path:

- Start app on a random local port with
  `clearfolio.tenant-claims.hmac-secret` and
  `clearfolio.artifact-link-ledger.path` plus
  `clearfolio.analytics-snapshot-ledger.path` configured.
- Runtime Java: 21.0.11.
- Verify `GET /`, `/assets/viewer/demo.js`, missing-auth KPI denial,
  unsigned tenant-claim KPI denial, authenticated empty KPI snapshot with signed
  tenant claims, document upload with signed tenant headers, status polling to
  `SUCCEEDED`, `/viewer/{docId}`, authenticated viewer bootstrap, signed
  artifact URL creation, unsigned artifact denial, signed artifact range
  access, artifact read audit lookup, artifact token revocation, revoked-token
  denial, cross-tenant status denial, post-upload KPI snapshot, and file-backed
  KPI snapshot ledger append evidence.

Result:

- Root shell: 200.
- Demo JS: 200.
- Missing-auth KPI: 401.
- Unsigned tenant-claim KPI with secret configured: 401.
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
- Artifact ledger file: present, 2 `ISSUED` lines, 1 `REVOKED` line,
  and 1 `READ` line.
- KPI snapshot ledger file: present, 2 `SNAPSHOT` lines.

Evidence:

- `smoke-local.txt`

## GitHub Checks

PR checks visible to this run:

- `coverage-evidence`: queued.
- `scan-pr-queue`: queued.
- `strix`: queued.

Review and queued checks are not treated as a blocker for continuing the
sale-readiness work.
