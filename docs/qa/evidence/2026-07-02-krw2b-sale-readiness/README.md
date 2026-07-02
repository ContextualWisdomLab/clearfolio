# KRW 2B Sale-Readiness Evidence

Date: 2026-07-02
Verification source head SHA before this evidence refresh:
`0e629ee49b3796e49045bce58106cfd9af9be918`

## Gate Summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Java runtime | Pass, OpenJDK 21.0.11 | `java-version.txt` |
| Compile warnings/deprecations | Pass | `compile.log` |
| Tests + JaCoCo | Pass, 335 tests, `classes=49`, `line_missed=0`, `branch_missed=0` | `mvn-test.log`, `test-jacoco.log`, `jacoco.csv`, `jacoco-status.txt` |
| JavaDoc | Pass, `javadoc_warnings_or_errors=none` | `javadoc.log`, `javadoc-status.txt` |
| Markdown lint | Pass, 0 errors across changed docs | `markdownlint.log` |
| JS syntax | Pass | `node-check.log` |
| SAST | Pass, 0 findings | `semgrep.log`, `semgrep.json` |
| SBOM | Pass, CycloneDX 1.6, 142 components, 0 components without license metadata | `sbom-cyclonedx.log`, `sbom-cyclonedx.json`, `sbom-status.txt` |
| License review | Partial, policy checker reports 136 allowed components, 6 review-required components, 0 unlisted violations; legal decisions still needed | `docs/security/2026-07-02-license-allowlist-review.md`, `license-policy-summary.json`, `license-policy-test.log` |
| Auth/tenant, signed artifacts, and KPI snapshots | Partial, runtime tenant enforcement, optional gateway HMAC tenant-claim validation, signed artifact tokens, token revocation, artifact read audit API, optional file-backed artifact-link ledger replay, optional file-backed KPI snapshot ledger replay, and tenant-scoped KPI snapshot export API implemented; OIDC/JWT and centralized durable revocation/audit/analytics persistence pending | `docs/security/2026-07-02-auth-tenant-model.md`, `docs/security/2026-07-02-signed-artifact-link-design.md`, auth/artifact/analytics tests |
| Buyer deployment integration | Pass for buyer sandbox scope; `buyer-demo` Spring profile, gateway-signed header contract, connector API table, OpenAPI connector seed, smoke path, and cutover gates are documented; buyer tenant import and production OIDC/JWT profile remain follow-up | `src/main/resources/application-buyer-demo.yml`, `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`, `docs/deployment/clearfolio-buyer-connector.openapi.yaml` |
| Durable job repository design, state-store, and lifecycle event slice | Partial, code boundary implemented; `ConversionJobStateStore` routes worker success/failure and operator retry transitions, and `ConversionJobLifecycleEvent` now records process-local append-only transition evidence, while SQL persistence and restart recovery remain pending | `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`, state-store and lifecycle event tests |
| Seeded buyer-demo screenshots | Pass for local screenshot scope; seeded desktop and mobile viewports render after `Load demo story`, with no mobile horizontal overflow and uploaded FigJam screenshot nodes `25:1423` and `25:1422` | `seeded-demo-story-verification.md`, `screenshots/seeded-demo-desktop-viewport.png`, `screenshots/seeded-demo-mobile-viewport.png` |
| Buyer diligence closure map | Pass for FigJam handoff scope; added `Clearfolio KRW 2B Buyer Diligence Closure Map` on the existing evidence board, and captured Slides generation prerequisites plus deck outline | `docs/design/2026-07-03-buyer-diligence-slides-and-closure-map.md`, `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md` |
| Local smoke | Pass, signed tenant claims plus file-backed artifact/KPI ledgers, KPI snapshot export API, buyer-demo KPI evidence panel, and operator recovery evidence panel | `smoke-local.txt`, `smoke-app.log`, `smoke-ui-root.txt` |
| GitHub PR state | Queued checks; review required | `gh-pr-state.json`, `gh-pr-checks.txt` |

## SAST

Command:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Result:

- Semgrep completed successfully.
- Rules run: 60 Java rules.
- Targets scanned: 53 tracked files.
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
- `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`
- `docs/deployment/clearfolio-buyer-connector.openapi.yaml`
- `src/main/resources/application-buyer-demo.yml`
- `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobStateStore.java`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobLifecycleEvent.java`
- `src/main/java/com/clearfolio/viewer/repository/RepositoryBackedConversionJobStateStore.java`
- `docs/superpowers/plans/2026-07-02-conversion-job-lifecycle-events.md`
- `buyer-deployment-slice-verification.md`
- FigJam diagrams:
  [Clearfolio Gateway Signed Tenant Claims Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM)
  and `Clearfolio KPI Snapshot Evidence Ledger Flow` plus
  `Clearfolio KPI Snapshot Export Evidence API Flow` and
  `Clearfolio Buyer Demo KPI Evidence Panel Flow` plus
  `Clearfolio Operator Recovery Evidence Flow` and
  `Clearfolio Conversion State Store Implementation Flow` plus
  `Clearfolio Conversion Lifecycle Event Trail Flow`.

## Local Smoke

Command path:

- Start app on a random local port with
  `clearfolio.tenant-claims.hmac-secret` and
  `clearfolio.artifact-link-ledger.path` plus
  `clearfolio.analytics-snapshot-ledger.path` configured.
- Runtime Java: 21.0.11.
- Verify `GET /`, buyer-demo KPI evidence panel markup,
  buyer-demo operator recovery evidence panel markup, `/assets/viewer/demo.js`,
  demo JS KPI export endpoint reference,
  missing-auth KPI denial, unsigned tenant-claim KPI denial, authenticated empty
  KPI snapshot with signed tenant claims, authenticated empty KPI export lookup,
  document upload with signed tenant headers, status polling to `SUCCEEDED`,
  `/viewer/{docId}`, authenticated viewer bootstrap, signed artifact URL
  creation, unsigned artifact denial, signed artifact range access, artifact
  read audit lookup, artifact token revocation, revoked-token denial,
  cross-tenant status denial, post-upload KPI snapshot, post-upload KPI export
  lookup, and file-backed KPI snapshot ledger append evidence.

Result:

- Root shell: 200.
- Root shell evidence panel: present.
- Root shell operator recovery panel: present.
- Demo JS: 200.
- Demo JS KPI export endpoint reference: present.
- Missing-auth KPI: 401.
- Unsigned tenant-claim KPI with secret configured: 401.
- Authenticated empty KPI: 200.
- Authenticated empty KPI exports: 200, 1 record, tenant id omitted.
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
- Post-upload KPI exports: 200, 2 records, latest `totalJobs=1`, tenant id
  omitted.
- Artifact ledger file: present, 2 `ISSUED` lines, 1 `REVOKED` line,
  and 1 `READ` line.
- KPI snapshot ledger file: present, 2 `SNAPSHOT` lines.

Evidence:

- `smoke-local.txt`
- `smoke-ui-root.txt`

## GitHub Checks

PR checks visible to this run:

- `coverage-evidence`: queued.
- `scan-pr-queue`: queued.
- `strix`: queued.

Review and queued checks are not treated as a blocker for continuing the
sale-readiness work.
