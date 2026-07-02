# KRW 2B Sale-Readiness Evidence

Date: 2026-07-02
Head SHA: `7236dea339dad2584c772b3d44ce1f98063b5ba9`

## Gate Summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Compile warnings/deprecations | Pass | `compile.log` |
| Tests + JaCoCo | Pass, `classes=32`, `line_missed=0`, `branch_missed=0` | `test-jacoco.log`, `jacoco.csv`, `jacoco-status.txt` |
| JavaDoc | Pass, `javadoc_warnings_or_errors=none` | `javadoc.log`, `javadoc-status.txt` |
| Markdown lint | Pass, 0 errors across changed docs | `markdownlint.log` |
| JS syntax | Pass | `node-check.log` |
| SAST | Pass, 0 findings | `semgrep.log`, `semgrep.json` |
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
- Targets scanned: 33 tracked files.
- Findings: 0.
- Errors: 0.

Evidence:

- `semgrep.json`

## Local Smoke

Command path:

- Start app on `localhost:18080`.
- Verify `GET /`, `/assets/viewer/demo.js`, empty KPI snapshot, document upload,
  status polling to `SUCCEEDED`, `/viewer/{docId}`, and post-upload KPI snapshot.

Result:

- Root shell: 200.
- Demo JS: 200.
- Final conversion status: `SUCCEEDED`.
- Viewer HTML: 200.
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
