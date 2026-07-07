# Buyer Demo Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a buyer-demoable upload and session-history front door for Clearfolio Viewer without changing the existing API contract.

**Architecture:** Reuse the current Spring `ViewerUiController` and static asset pattern. Serve a root HTML shell at `/`, post uploads to the existing `/api/v1/convert/jobs` endpoint, poll `/api/v1/convert/jobs/{jobId}`, and link successful jobs to the existing `/viewer/{docId}` route.

**Tech Stack:** Java 21, Spring Boot WebFlux, vanilla JavaScript modules, existing `viewer.css`, no new frontend framework or dependency.

## Global Constraints

- Preserve Figma Code Connect exclusion.
- Do not add a new library, Git submodule, or frontend framework.
- Keep the current Maven gates: warning-free compile, full tests, JaCoCo 100 percent line/branch, JavaDoc, changed-doc Markdown lint, and PR security evidence.
- Keep the existing `/api/v1/convert/jobs`, `/api/v1/convert/jobs/{jobId}`, and `/viewer/{docId}` contracts.

---

### Task 1: Root Buyer Demo Shell

**Files:**
- Modify: `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`
- Modify: `src/test/java/com/clearfolio/viewer/controller/ViewerUiControllerTest.java`
- Create: `src/main/resources/static/assets/viewer/demo.js`
- Modify: `src/main/resources/static/assets/viewer/viewer.css`

**Interfaces:**
- Consumes: existing `DocumentConversionService` constructor dependency.
- Produces: `GET /` HTML shell, static `/assets/viewer/demo.js`, and CSS classes shared with `viewer.css`.

- [ ] **Step 1: Write failing shell test**

Add a test asserting `GET /` returns an upload form, session-history region, KPI strip, and `/assets/viewer/demo.js` script reference.

- [ ] **Step 2: Run focused test red**

Run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH mvn -q -Dtest=ViewerUiControllerTest#homeReturnsBuyerDemoUploadShell test
```

Expected: fails because `/` is not implemented.

- [ ] **Step 3: Write failing static-script test**

Add a test asserting `demo.js` exists and contains the existing API paths, `FormData`, `localStorage`, and viewer links.

- [ ] **Step 4: Run focused script test red**

Run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH mvn -q -Dtest=ViewerUiControllerTest#demoScriptUsesExistingApiAndSessionHistory test
```

Expected: fails because `demo.js` does not exist.

- [ ] **Step 5: Implement minimal shell and script**

Add `ViewerUiController.home()` for `/`, a small `demoShellHtml()` template, vanilla `demo.js`, and CSS classes for upload, KPI, and history panels.

- [ ] **Step 6: Run focused tests green**

Run:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH mvn -q -Dtest=ViewerUiControllerTest test
```

Expected: all `ViewerUiControllerTest` tests pass.

### Task 2: Sale-Readiness Evidence

**Files:**
- Modify: `docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md`

**Interfaces:**
- Consumes: Phase 1 plan.
- Produces: updated evidence pointer for buyer-demo shell.

- [ ] **Step 1: Update plan evidence**

Add a short Phase 1 progress note naming `/` as the buyer-demo upload/history shell.

- [ ] **Step 2: Run changed-doc lint**

Run:

```bash
markdownlint-cli2 docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md docs/superpowers/plans/2026-07-02-buyer-demo-shell.md
```

Expected: 0 errors.

### Task 3: Full Gate And PR Update

**Files:**
- All changed files from Tasks 1 and 2.

**Interfaces:**
- Produces: pushed update to `codex/krw2b-sale-readiness` and PR #74.

- [ ] **Step 1: Run full Maven gates**

Run compile, JaCoCo/test/report, and JavaDoc with JDK 21.

- [ ] **Step 2: Run SAST**

Run:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Expected: 0 findings.

- [ ] **Step 3: Commit and push**

Commit the implementation and push the same branch so PR #74 includes the first buyer-demo product slice.
