# Conversion Job State Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first code-level durable-persistence precondition by routing conversion lifecycle changes through an explicit `ConversionJobStateStore`.

**Architecture:** Keep the current `ConversionJobRepository` read/dedupe boundary. Add a small lifecycle transition interface, implement it on the existing in-memory repository, and update worker/operator retry code to call the explicit transition boundary.

**Tech Stack:** Java 21-compatible Spring Boot, JUnit 5, Maven, existing in-memory repository.

## Global Constraints

- Do not add SQL, Flyway, a new dependency, a submodule, or a split library in this slice.
- Preserve the existing `ConversionJobRepository` read and dedupe API.
- Preserve tenant-scoped status lookup behavior and current retry/dead-letter semantics.
- Keep Figma Code Connect unused.
- Run `mvn -DskipTests compile`, `mvn test`, JaCoCo missed count check, JavaDoc, markdownlint for changed docs, Semgrep, and license policy check before claiming completion.

---

### Task 1: State Store Contract

**Files:**

- Create: `src/main/java/com/clearfolio/viewer/repository/ConversionJobStateStore.java`
- Modify: `src/main/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepository.java`
- Test: `src/test/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepositoryTest.java`

**Interfaces:**

- Consumes: `ConversionJob`, `ConversionJobRepository`
- Produces: `ConversionJobStateStore` with `claimForProcessing`, `scheduleRetry`, `markSucceeded`, `markDeadLettered`, and `retryDeadLettered`

- [x] **Step 1: Write failing repository tests**

Add tests proving the in-memory repository can be assigned to
`ConversionJobStateStore`, claims ready jobs, schedules retries, accepts
dead-letter retry, and does not downgrade succeeded jobs on queue saturation.

- [x] **Step 2: Run red test**

Run:

```bash
mvn -Dtest=InMemoryConversionJobRepositoryTest test
```

Expected: compile failure because `ConversionJobStateStore` does not exist.

- [x] **Step 3: Add minimal implementation**

Create the interface and implement it directly on
`InMemoryConversionJobRepository`.

- [x] **Step 4: Run green test**

Run:

```bash
mvn -Dtest=InMemoryConversionJobRepositoryTest test
```

Expected: all repository tests pass.

### Task 2: Worker Transition Routing

**Files:**

- Modify: `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`
- Modify: `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`
- Test: `src/test/java/com/clearfolio/viewer/service/DefaultConversionWorkerTest.java`
- Test: `src/test/java/com/clearfolio/viewer/service/DefaultDocumentConversionServiceTest.java`

**Interfaces:**

- Consumes: `ConversionJobStateStore`
- Produces: worker and operator retry paths that use explicit transition calls

- [x] **Step 1: Write focused worker test**

Add one test with a recording state store so worker success calls
`claimForProcessing` and `markSucceeded`.

- [x] **Step 2: Run red worker test**

Run:

```bash
mvn -Dtest=DefaultConversionWorkerTest test
```

Expected: failure because the worker still mutates jobs directly.

- [x] **Step 3: Refactor worker and operator retry**

Inject `ConversionJobStateStore` in Spring constructors while preserving
test-only constructors that derive it from repositories implementing the
interface.

- [x] **Step 4: Run green worker/service tests**

Run:

```bash
mvn -Dtest=DefaultConversionWorkerTest,DefaultDocumentConversionServiceTest test
```

Expected: both test classes pass.

### Task 3: Evidence and Handoff

**Files:**

- Modify: `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
- Modify: `docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md`
- Modify: `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`
- Modify: `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/buyer-deployment-slice-verification.md`
- Modify: `docs/qa/evidence/LATEST.md`

**Interfaces:**

- Consumes: passing test and gate output
- Produces: buyer-readable evidence that the first durable-persistence precondition is implemented

- [x] **Step 1: Update docs**

Document that `ConversionJobStateStore` exists in code and that SQL
persistence remains intentionally unimplemented.

- [x] **Step 2: Generate FigJam transition diagram**

Add a FigJam diagram to the existing board for the state-store implementation
flow. Do not use Figma Code Connect.

- [x] **Step 3: Run gates**

Run the global constraint verification commands and record the results.

- [x] **Step 4: Commit and push**

Commit with:

```bash
git commit -m "feat: add conversion job state store"
```
