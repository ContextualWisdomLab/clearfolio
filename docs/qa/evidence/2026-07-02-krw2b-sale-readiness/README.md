# KRW 2B Sale-Readiness Evidence

Date: 2026-07-02
Verification source head SHA before this evidence refresh:
`7df3ac8b8253cd1a445ba7faddbf99bc9a5c5fcd`

## Gate Summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Java runtime | Pass, Java 26.0.1 runtime with Java 21 release-target compile | `java-version.txt`, `compile.log` |
| Compile warnings/deprecations | Pass | `compile.log` |
| Tests + JaCoCo | Pass, 357 tests, `classes=49`, `line_missed=0`, `branch_missed=0` | `mvn-test.log`, `test-jacoco.log`, `jacoco.csv`, `jacoco-status.txt` |
| JavaDoc | Pass, `javadoc_warnings_or_errors=none` | `javadoc.log`, `javadoc-status.txt` |
| Markdown lint | Pass, 0 errors across changed docs | `markdownlint.log` |
| JS syntax | Pass | `node-check.log` |
| SAST | Pass, 0 findings | `semgrep.log`, `semgrep.json` |
| SBOM | Pass, CycloneDX 1.6, 61 components, 0 components without license metadata | `sbom-cyclonedx.log`, `sbom-cyclonedx.json`, `sbom-status.txt` |
| License review | Pass, buyer-release policy checker reports 61 allowed components, 0 review-required components, 0 unlisted violations, and passes `--require-no-review` | `docs/security/2026-07-02-license-allowlist-review.md`, `license-policy-summary.json`, `license-policy-test.log` |
| Third-party attribution | Pass, generated buyer data-room attribution contains all 61 current SBOM components and passes drift check | `docs/legal/2026-07-03-third-party-attribution.md`, `third-party-attribution-check.log` |
| Buyer data-room manifest | Pass, manifest references required buyer evidence artifacts, all local paths exist, and ready gates reference only ready artifacts | `docs/diligence/2026-07-03-buyer-data-room-manifest.json`, `buyer-dataroom-manifest-check.log` |
| Buyer readiness scorecard | Pass, generated scorecard reports 23 artifacts, 8 readiness gates, 38 percent conservative gate readiness, and ready-gate evidence integrity pass from the current data-room manifest | `docs/diligence/2026-07-03-buyer-readiness-scorecard.md`, `buyer-readiness-scorecard-summary.json` |
| Figma Slides generation payload | Pass, payload check reports 11 slides, 4 objectives, and 0 errors; actual Slides generation still requires Figma team or organization plan selection | `docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json`, `figma-deck-payload-check.json` |
| Auth/tenant, signed artifacts, and KPI snapshots | Partial, runtime tenant enforcement, optional gateway HMAC tenant-claim validation, production-profile fail-closed startup without signed tenant secret, signed artifact tokens, token revocation, artifact read audit API, optional file-backed artifact-link ledger replay, optional file-backed KPI snapshot ledger replay, and tenant-scoped KPI snapshot export API implemented; OIDC/JWT and centralized durable revocation/audit/analytics persistence pending | `docs/security/2026-07-02-auth-tenant-model.md`, `docs/security/2026-07-02-signed-artifact-link-design.md`, auth/artifact/analytics tests |
| Buyer deployment integration | Pass for buyer sandbox scope; `buyer-demo` Spring profile, gateway-signed header contract, connector API table, OpenAPI connector seed, smoke path, and cutover gates are documented; buyer tenant import and production OIDC/JWT profile remain follow-up | `src/main/resources/application-buyer-demo.yml`, `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`, `docs/deployment/clearfolio-buyer-connector.openapi.yaml` |
| Durable job repository design, state-store, lifecycle event, and recovery sweep slice | Partial, code boundary implemented; `ConversionJobStateStore` routes worker success/failure and operator retry transitions, `ConversionJobLifecycleEvent` records process-local append-only transition evidence, and `DefaultConversionWorker` now re-enqueues due submitted jobs plus stale processing leases from available repository state, while SQL persistence remains pending for true process-restart durability | `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`, state-store, lifecycle event, and recovery sweep tests |
| Seeded buyer-demo screenshots | Pass for local screenshot scope; seeded desktop and mobile viewports render after `Load demo story`, with no mobile horizontal overflow and uploaded FigJam screenshot nodes `25:1423` and `25:1422` | `seeded-demo-story-verification.md`, `screenshots/seeded-demo-desktop-viewport.png`, `screenshots/seeded-demo-mobile-viewport.png` |
| Buyer diligence closure map | Pass for FigJam handoff scope; added `Clearfolio KRW 2B Buyer Diligence Closure Map`, `Clearfolio Buyer Readiness Scorecard Gate Map`, and `Clearfolio Buyer Diligence Slides Storyboard` on the existing evidence board, and captured Slides generation prerequisites plus deck outline | `docs/design/2026-07-03-buyer-diligence-slides-and-closure-map.md`, `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md` |
| Local smoke | Pass, signed tenant claims plus file-backed artifact/KPI ledgers, KPI snapshot export API, buyer-demo KPI evidence panel, and operator recovery evidence panel | `smoke-local.txt`, `smoke-app.log`, `smoke-ui-root.txt` |
| GitHub PR state | Seeded buyer-demo story branch is refreshed on current `main`; review and queued checks are not treated as blockers | PR body and GitHub UI |

## SAST

Command:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Result:

- Semgrep completed successfully.
- Rules run: 60 Java rules.
- Targets scanned: 56 tracked files.
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
- Components: 61.
- Components without license metadata: 0.
- Unique license metadata entries: 3.
- Engineering license review is now documented in
  `docs/security/2026-07-02-license-allowlist-review.md`.
- The unused `tika-parsers-standard-package` dependency was removed, which
  eliminated Tika transitive review-required components `jhighlight`, `junrar`,
  and `juniversalchardet` from the current SBOM.
- Spring Boot's default Logback starter was replaced with
  `spring-boot-starter-log4j2`, and `jakarta.annotation-api` is excluded from
  the current starter paths.
- The standard-library license policy checker passes buyer-release mode:
  61 allowed components, 0 review-required components, and 0 unlisted
  violations with `--require-no-review`.
- The standard-library attribution renderer generates
  `docs/legal/2026-07-03-third-party-attribution.md` from the same SBOM and
  the drift check confirms that the data-room attribution file is current.
- The buyer data-room manifest checker confirms the sale-readiness package links
  to required local evidence and current Figma/GitHub handoff URLs, and prevents
  ready gates from citing partial or external artifacts as complete evidence.
- The buyer readiness scorecard generator reports 23 current data-room
  artifacts, 8 readiness gates, 38 percent conservative gate readiness, and
  ready-gate evidence integrity pass while keeping partial gates as discount
  risks.
- The Figma Slides payload checker confirms the buyer diligence deck payload has
  11 slides, 4 objectives, explicit no-Code-Connect wording, readiness
  scorecard content, discount-risk content, and claim-boundary wording.

Evidence:

- `sbom-cyclonedx.log`
- `sbom-cyclonedx.json`
- `sbom-status.txt`
- `license-policy.log`
- `license-policy-summary.json`
- `license-policy-test.log`
- `third-party-attribution-check.log`
- `buyer-dataroom-manifest-check.log`
- `buyer-readiness-scorecard-summary.json`
- `figma-deck-payload-check.json`
- `docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json`
- `docs/legal/2026-07-03-third-party-attribution.md`
- `docs/security/2026-07-02-license-allowlist-review.md`
- `docs/security/2026-07-02-license-policy.json`
- `docs/security/2026-07-02-auth-tenant-model.md`
- `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`
- `docs/deployment/clearfolio-buyer-connector.openapi.yaml`
- `src/main/resources/application-buyer-demo.yml`
- `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobStateStore.java`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobRepository.java`
- `src/main/java/com/clearfolio/viewer/repository/ConversionJobLifecycleEvent.java`
- `src/main/java/com/clearfolio/viewer/repository/RepositoryBackedConversionJobStateStore.java`
- `docs/superpowers/plans/2026-07-02-conversion-job-lifecycle-events.md`
- `docs/superpowers/plans/2026-07-03-conversion-recovery-sweep.md`
- `buyer-deployment-slice-verification.md`
- FigJam diagrams:
  [Clearfolio Gateway Signed Tenant Claims Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM)
  and `Clearfolio KPI Snapshot Evidence Ledger Flow` plus
  `Clearfolio KPI Snapshot Export Evidence API Flow` and
  `Clearfolio Buyer Demo KPI Evidence Panel Flow` plus
  `Clearfolio Operator Recovery Evidence Flow` and
  `Clearfolio Conversion State Store Implementation Flow` plus
  `Clearfolio Conversion Lifecycle Event Trail Flow` plus
  `Clearfolio Buyer Readiness Scorecard Gate Map` plus
  `Clearfolio Buyer Diligence Slides Storyboard` plus
  `Clearfolio Ready Gate Evidence Integrity Check` plus
  `Clearfolio Conversion Recovery Sweep Flow`.

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

This evidence refresh was produced locally before publishing the recovery-sweep
branch. The PR body should carry the local gate results from this file. Review
and queued GitHub checks are not treated as blockers for continuing the
sale-readiness work.
