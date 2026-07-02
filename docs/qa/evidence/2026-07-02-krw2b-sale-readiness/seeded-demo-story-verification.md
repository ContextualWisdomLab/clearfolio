# Seeded Buyer Demo Story Verification

Date: 2026-07-03

Branch: `codex/seeded-buyer-demo-story`

## Scope

This slice adds a static buyer-demo story for local screenshots, FigJam review,
and buyer-deck preparation. It does not add a backend seed endpoint, database
fixture, frontend framework, separate library, or Git submodule.

## Artifacts

- UI control:
  `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`
- Loader:
  `src/main/resources/static/assets/viewer/demo.js`
- Fixture:
  `src/main/resources/static/assets/viewer/demo-fixtures.json`
- Test:
  `src/test/java/com/clearfolio/viewer/controller/ViewerUiControllerTest.java`
- FigJam:
  `Clearfolio Seeded Buyer Demo Story Flow`
  on <https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM>

## TDD Evidence

RED:

- `mvn -Dtest=ViewerUiControllerTest test`
- Expected failures before implementation:
  missing `Load demo story` button, missing fixture URL/loader hook, and missing
  fixture resource.

GREEN:

- `mvn -Dtest=ViewerUiControllerTest test`
- Result: 7 tests, 0 failures, 0 errors, 0 skipped.

## Fresh Gate Evidence

| Gate | Command | Result |
| --- | --- | --- |
| Compile | `mvn -DskipTests compile` | Build success; no compiler warnings emitted. |
| Full test suite | `mvn test` | 336 tests, 0 failures, 0 errors, 0 skipped. |
| JaCoCo | `awk -F, 'NR>1 && $2 ~ /com.clearfolio.viewer/ {line_missed += $4; branch_missed += $6; classes += 1} END {printf("classes=%d line_missed=%d branch_missed=%d\\n", classes, line_missed, branch_missed)}' target/site/jacoco/jacoco.csv` | `classes=49 line_missed=0 branch_missed=0`. |
| JavaScript syntax | `node --check src/main/resources/static/assets/viewer/demo.js` | Exit 0. |
| JavaDoc | `mvn -q -DskipTests javadoc:javadoc` | Exit 0; no output. |
| Markdown lint | `npx markdownlint-cli2 README.md docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md docs/diligence/2026-07-02-buyer-diligence-index.md docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md docs/superpowers/plans/2026-07-03-seeded-buyer-demo-story.md` | 5 files, 0 errors. |
| Semgrep | `uvx semgrep --config p/java --metrics=off --error --json --output /tmp/clearfolio-seeded-demo-semgrep.json src/main/java src/test/java` | 53 Java files scanned, 60 rules, 0 findings. |
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json` | 142 components, 136 allowed, 6 review-required, 0 violations. |
| Diff hygiene | `git diff --check` | Exit 0. |

## Claim Boundary

The fixture is deterministic local demo evidence for Product Design, Data
Analytics, FigJam, screenshots, and buyer-deck review. It is not production data,
not an analytics source of truth, and not a durable persistence substitute.
