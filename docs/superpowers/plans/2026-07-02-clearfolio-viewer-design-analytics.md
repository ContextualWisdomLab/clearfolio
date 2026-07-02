# Clearfolio Viewer Design Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the no-Code-Connect Clearfolio viewer design plan into a safe,
reviewable follow-up implementation path grounded in the live PR queue, repo UI
state, and Figma design artifact.

**Architecture:** Keep the existing Java WebFlux HTML shell and static
`viewer.css`/`viewer.js` architecture. Use Figma and Markdown artifacts for
decision alignment before any source change. If code changes are needed, make a
single narrow patch in the existing viewer files and cover it with existing
controller/static-resource tests.

**Tech Stack:** Java 21, Spring Boot WebFlux, Maven, static CSS/JavaScript,
GitHub CLI, Figma Plugin API.

## Global Constraints

- Do not use Figma Code Connect.
- Do not add a frontend framework or UI build pipeline.
- Do not add runtime dependencies for viewer polish.
- Preserve `mvn -DskipTests compile`.
- Preserve `mvn test`.
- Preserve JaCoCo 100% line/branch coverage for `com.clearfolio.viewer.*`.
- Preserve JavaDoc gate: `mvn -q -DskipTests javadoc:javadoc`.
- Preserve markdown lint for changed docs.
- Attach or reference security evidence on implementation PRs.

---

## File Structure

- `docs/design/clearfolio-viewer-plan/README.md` explains the current decision.
- `docs/design/clearfolio-viewer-plan/pr-queue-analytics.md` records the live
  PR queue snapshot and recommendation.
- `docs/design/clearfolio-viewer-plan/product-design-audit.md` records the
  Product Design brief and audit.
- `docs/design/clearfolio-viewer-plan/figma-handoff.md` records the Figma file
  and no-Code-Connect constraints.
- `docs/design/clearfolio-viewer-plan/figma-board-screenshot.png` provides a
  local visual proof of the Figma board.
- Future code work, if approved after queue consolidation, should touch only:
  `src/main/resources/static/assets/viewer/viewer.css`,
  `src/main/resources/static/assets/viewer/viewer.js`,
  `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`, and
  matching tests under `src/test/java/com/clearfolio/viewer/controller/`.

### Task 1: Refresh Live Queue Evidence

**Files:**
- Modify: `docs/design/clearfolio-viewer-plan/pr-queue-analytics.md`

**Interfaces:**
- Consumes: GitHub PR metadata from `gh pr list`.
- Produces: current queue counts and a canonical UX/security/performance lane
  recommendation.

- [ ] **Step 1: Collect live PR metadata**

Run:

```bash
gh pr list --repo ContextualWisdomLab/clearfolio \
  --state open \
  --limit 200 \
  --json number,title,headRefName,baseRefName,isDraft,mergeStateStatus,reviewDecision,updatedAt,createdAt,author,labels,url
```

Expected: JSON array of open PRs.

- [ ] **Step 2: Recompute queue counts**

Run:

```bash
gh pr list --repo ContextualWisdomLab/clearfolio \
  --state open \
  --limit 200 \
  --json number,title,mergeStateStatus,reviewDecision \
  --jq '{total:length, byMergeState:(group_by(.mergeStateStatus)|map({state:.[0].mergeStateStatus,count:length})), byReview:(group_by(.reviewDecision)|map({decision:.[0].reviewDecision,count:length}))}'
```

Expected: `total` and grouped counts. Update the report tables with the new
numbers.

- [ ] **Step 3: Reclassify themes**

Use these title rules:

```text
UX/Palette: Palette, UX, 접근성, 로딩, 새로고침, 링크, a11y, accessibility
Security/Sentinel: Sentinel, 보안, XSS, security, 널 바이트, Null, HSTS
Performance/Bolt: Bolt, 성능, HexFormat, hex, performance, optimization, 최적화
Product/Platform: all remaining PRs
```

Expected: each open PR maps to exactly one theme.

- [ ] **Step 4: Commit refreshed report**

Run:

```bash
git add docs/design/clearfolio-viewer-plan/pr-queue-analytics.md
git commit -m "docs: refresh clearfolio viewer queue analytics"
```

Expected: commit includes only refreshed analytics text.

### Task 2: Choose One Viewer UX Source Of Truth

**Files:**
- Modify: `docs/design/clearfolio-viewer-plan/product-design-audit.md`
- Modify: `docs/design/clearfolio-viewer-plan/figma-handoff.md`

**Interfaces:**
- Consumes: the UX/Palette PR list and Figma board.
- Produces: one canonical UX branch or a decision to extract a minimal patch.

- [ ] **Step 1: List UX candidate PRs**

Run:

```bash
gh pr list --repo ContextualWisdomLab/clearfolio \
  --state open \
  --limit 200 \
  --json number,title,headRefName,mergeStateStatus,reviewDecision,url \
  --jq '.[] | select(.title|test("Palette|UX|접근성|로딩|새로고침|링크|a11y|accessibility"; "i")) | {number,title,headRefName,mergeStateStatus,reviewDecision,url}'
```

Expected: only viewer UX/accessibility/loading/link PR candidates.

- [ ] **Step 2: Inspect the best candidate PR**

Run:

```bash
gh pr view PR_NUMBER --repo ContextualWisdomLab/clearfolio \
  --json number,title,headRefName,mergeStateStatus,reviewDecision,statusCheckRollup,latestReviews,url
```

Expected: current merge, review, and check context for the candidate.

- [ ] **Step 3: Record the chosen source**

Update both design docs with:

```md
Canonical UX source: PR #PR_NUMBER, `<headRefName>`.
Reason: [one sentence based on current evidence].
Implementation mode: adopt branch, extract minimal patch, or defer.
```

Expected: Figma and audit docs name the same source.

- [ ] **Step 4: Commit source-of-truth decision**

Run:

```bash
git add docs/design/clearfolio-viewer-plan/product-design-audit.md docs/design/clearfolio-viewer-plan/figma-handoff.md
git commit -m "docs: record clearfolio viewer ux source of truth"
```

Expected: commit includes only decision docs.

### Task 3: Implement Minimal Viewer Patch Only If Needed

**Files:**
- Modify: `src/main/resources/static/assets/viewer/viewer.css`
- Modify: `src/main/resources/static/assets/viewer/viewer.js`
- Modify: `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`
- Modify: `src/test/java/com/clearfolio/viewer/controller/ViewerUiControllerTest.java`

**Interfaces:**
- Consumes: chosen UX source from Task 2.
- Produces: one minimal viewer behavior or copy/style patch.

- [ ] **Step 1: Confirm the patch is not already on `main`**

Run:

```bash
git diff origin/main -- src/main/resources/static/assets/viewer/viewer.css src/main/resources/static/assets/viewer/viewer.js src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java
```

Expected: no unreviewed implementation diff before editing.

- [ ] **Step 2: Write or update the focused test first**

For HTML shell copy or structure changes, update:

```text
src/test/java/com/clearfolio/viewer/controller/ViewerUiControllerTest.java
```

Expected test target: the exact label, hidden operator action, status text, or
static asset reference that the patch changes.

- [ ] **Step 3: Run the focused test**

Run:

```bash
mvn -Dtest=ViewerUiControllerTest test
```

Expected: fail if the test describes new behavior not yet implemented; pass if
the chosen patch is already present.

- [ ] **Step 4: Implement the smallest viewer change**

Edit only the files required by the failing focused test. Reuse the existing
token names and DOM IDs. Do not add dependencies, new controllers, or new
frontend build tooling.

- [ ] **Step 5: Re-run focused test**

Run:

```bash
mvn -Dtest=ViewerUiControllerTest test
```

Expected: pass.

- [ ] **Step 6: Run repository gates**

Run:

```bash
mvn -DskipTests compile
mvn test
mvn -q -DskipTests javadoc:javadoc
```

Expected: all commands exit 0.

- [ ] **Step 7: Commit implementation**

Run:

```bash
git add src/main/resources/static/assets/viewer/viewer.css src/main/resources/static/assets/viewer/viewer.js src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java src/test/java/com/clearfolio/viewer/controller/ViewerUiControllerTest.java
git commit -m "fix: align viewer ux state handling"
```

Expected: one narrow implementation commit.

## Self-Review

- Spec coverage: analytics, Product Design audit, Figma handoff, and a minimal
  implementation route are covered.
- Placeholder scan: no `TBD` or `TODO` placeholders are allowed in this plan.
- Type consistency: future code work keeps the existing static viewer IDs:
  `doc-meta`, `live-status`, `error`, `retry-btn`, `open-json-link`, and
  `preview`.
