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
- Screenshots:
  `screenshots/seeded-demo-desktop-viewport.png`,
  `screenshots/seeded-demo-mobile-viewport.png`,
  `screenshots/seeded-demo-desktop.png`, and
  `screenshots/seeded-demo-mobile.png`
- FigJam screenshot nodes:
  desktop `25:1423`, mobile `25:1422`

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
| Markdown lint | `npx markdownlint-cli2 $(git diff --name-only origin/main -- '*.md')` | 7 files, 0 errors. |
| Semgrep | `uvx semgrep --config p/java --metrics=off --error --json --output /tmp/clearfolio-seeded-demo-semgrep.json src/main/java src/test/java` | 53 Java files scanned, 60 rules, 0 findings. |
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json --require-no-review` | 61 components, 61 allowed, 0 review-required, 0 violations. |
| Diff hygiene | `git diff --check` | Exit 0. |

## Browser Screenshot Evidence

Target flow:

`GET /` -> `Load demo story` -> seeded KPI, evidence, recovery, and history
state renders for desktop and mobile screenshots.

Environment:

- Browser path: in-app Browser.
- URL: <http://localhost:18086/>
- Desktop: default browser viewport.
- Mobile: explicit `390x844` viewport.

Checks:

| Check | Result |
| --- | --- |
| Page identity | URL `http://localhost:18086/`, title `Clearfolio Viewer`. |
| Not blank | DOM contained `Load demo story`, `Runtime jobs`, and `Session history`. |
| Framework overlay | No framework error overlay observed. |
| Console health | 0 warning/error entries before and after seed load. |
| Interaction proof | `Load demo story` clicked; status text became `Seeded buyer-demo story loaded for screenshot and Figma review.` |
| Seeded KPI proof | `total=4`, `ready=1`, `successRate=50%`, `p95=4200 ms`, `exports=1`. |
| Seeded recovery proof | `needsAction=2`, `retryReady=1`. |
| Seeded state proof | `board-pack-q3.pdf`, `supplier-contract.docx`, `legacy-approval.hwpx`, and `scanned-invoice.pdf` rendered with expected states. |
| Mobile layout proof | `clientWidth=375`, `scrollWidth=375`, `hasHorizontalOverflow=false`, first panel width `351`. |

Screenshot files:

- `screenshots/seeded-demo-desktop-viewport.png`
- `screenshots/seeded-demo-mobile-viewport.png`
- `screenshots/seeded-demo-desktop.png`
- `screenshots/seeded-demo-mobile.png`

FigJam upload:

- Desktop screenshot placed on node `25:1423`.
- Mobile screenshot placed on node `25:1422`.

## Review Thread Hardening

Follow-up fixes after PR review comments:

- Seeded job-detail inspection updates local session history without refreshing
  live KPI endpoints, so the deterministic seeded KPI/evidence state is not
  overwritten by an empty runtime snapshot.
- `Load demo story` renders the same 12-row history slice that is persisted to
  browser session storage.
- Fixture `submittedAt` values use explicit ISO-8601 timestamps with timezone
  so browser parsing is deterministic across engines.
- `ViewerUiControllerTest` verifies the seeded fixture timestamp format and the
  seeded-detail no-refresh path.

## Claim Boundary

The fixture is deterministic local demo evidence for Product Design, Data
Analytics, FigJam, screenshots, and buyer-deck review. It is not production data,
not an analytics source of truth, and not a durable persistence substitute.
