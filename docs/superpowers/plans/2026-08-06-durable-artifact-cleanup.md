# Durable Artifact Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the durable receipt ledger with authorized metadata tombstoning, exact-lifecycle artifact cleanup, restart-safe retries, bounded backpressure, and low-cardinality metrics.

**Architecture:** A single `ArtifactDeletionCoordinator` owns request creation, metadata tombstoning, cleanup transitions, immediate attempts, startup recovery, and scheduled bounded retries. `DefaultDocumentConversionService` delegates deletion while existing repository, artifact-store, and receipt-store interfaces remain replaceable for standalone and MSA deployments.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring scheduling and application events, Micrometer/Actuator, JUnit 5, Mockito, JaCoCo, Maven Javadoc.

## Global Constraints

- Every valid deletion request is durable before metadata mutation.
- Cross-tenant and missing requests perform no artifact or receipt access.
- Only controlled failure codes and bounded metric tags are persisted.
- Job identifiers remain permanently reserved; no receipt can bind to a new lifecycle.
- Recovery is serial and bounded by `max-receipts-per-run`.
- Production line and branch coverage remains 100%; public Javadocs are complete.
- `mvn -B --no-transfer-progress verify` is the complete local acceptance command.

---

### Task 1: Define failing coordinator contracts

**Files:**
- Create: `src/test/java/com/clearfolio/viewer/lifecycle/ArtifactDeletionCoordinatorTest.java`

**Interfaces:**
- Consumes: `ConversionJobRepository`, `ArtifactStore`, `ArtifactDeletionReceiptStore`.
- Produces: expected public methods `deleteForTenant(UUID, String)`, `deleteGlobally(UUID)`, and `retryPendingWork()`.

- [ ] **Step 1: Write failing tests** for successful lifecycle completion, cross-tenant no-access, artifact-read failure before tombstone, persisted delete failure, retry completion, restart replay, `DELETION_REQUESTED` recovery, digest mismatch, empty-digest late write cleanup, bounded batches, and invalid batch configuration.
- [ ] **Step 2: Run the focused test** with `mvn -B --no-transfer-progress -Dtest=ArtifactDeletionCoordinatorTest test`; expect compilation failure because the coordinator does not exist.
- [ ] **Step 3: Commit the RED contract** as `test(lifecycle): define durable cleanup integration contract`.

### Task 2: Add metrics and coordinator implementation

**Files:**
- Create: `src/main/java/com/clearfolio/viewer/lifecycle/ArtifactDeletionMetrics.java`
- Create: `src/main/java/com/clearfolio/viewer/lifecycle/ArtifactDeletionCoordinator.java`
- Modify: `pom.xml`
- Modify: `src/main/java/com/clearfolio/viewer/ClearfolioViewerApplication.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- `ArtifactDeletionMetrics(MeterRegistry, ArtifactDeletionReceiptStore)` registers `clearfolio.artifact.deletion.attempts{outcome=completed|failed}` and `clearfolio.artifact.deletion.pending`.
- `ArtifactDeletionCoordinator.deleteForTenant(UUID jobId, String tenantId)` returns true after an authorized metadata tombstone even when cleanup remains retryable.
- `ArtifactDeletionCoordinator.deleteGlobally(UUID jobId)` preserves the compatibility path while using receipts whenever job metadata exists.
- `ArtifactDeletionCoordinator.retryPendingWork()` processes at most the configured batch size.

- [ ] **Step 1: Add Spring Boot Actuator** without exposing new public management endpoints by default.
- [ ] **Step 2: Implement metrics** with only bounded outcome tags.
- [ ] **Step 3: Implement receipt-first deletion and recovery** for every nonterminal state, exact/sentinel digest validation, controlled failure codes, serial execution, startup replay, and fixed-delay retry.
- [ ] **Step 4: Enable scheduling and add configuration** for 30-second retry delay and 100-receipt batches.
- [ ] **Step 5: Run the focused test** and correct only implementation defects until it passes.
- [ ] **Step 6: Commit GREEN production** as `feat(lifecycle): integrate durable artifact cleanup worker`.

### Task 3: Delegate service deletion and preserve compatibility

**Files:**
- Modify: `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`
- Modify: `src/test/java/com/clearfolio/viewer/service/DefaultDocumentConversionServiceTest.java`

**Interfaces:**
- The existing six-argument service constructor remains available and creates a standalone coordinator.
- A Spring-autowired constructor accepts the shared coordinator.

- [ ] **Step 1: Change deletion tests first** to require receipt-backed failure recovery and to retain cross-tenant concealment.
- [ ] **Step 2: Run the focused service tests** and confirm they fail against the log-only implementation.
- [ ] **Step 3: Delegate both deletion entry points** and remove the exception-swallowing `deleteArtifact` method.
- [ ] **Step 4: Run coordinator and service tests** and verify success.
- [ ] **Step 5: Commit** as `fix(service): route deletions through durable cleanup`.

### Task 4: Complete documentation and release evidence

**Files:**
- Modify: `docs/security/2026-08-06-durable-artifact-deletion-receipts.md`
- Modify: `docs/superpowers/specs/2026-08-06-durable-artifact-cleanup-design.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Replace foundation-only statements** with the integrated state, retry, metrics, backpressure, and remaining transactional-database adapter limits.
- [ ] **Step 2: Record APA 7th references and operator recovery semantics.**
- [ ] **Step 3: Run Markdown lint and documentation contract tests.**
- [ ] **Step 4: Commit** as `docs: record durable artifact cleanup acceptance`.

### Task 5: Verify, consolidate the stack, and re-review

**Files:**
- No source file changes unless verification finds a reproducible defect.

- [ ] **Step 1: Run** `mvn -B --no-transfer-progress verify` and require zero failures, errors, skips, missed production lines, missed production branches, and Javadoc warnings.
- [ ] **Step 2: Verify exact-head fuzz, CI, Security Scan, SAST, Strix, CodeRabbit, OpenCode/Noema, and independent approval.**
- [ ] **Step 3: Fast-forward PR #268's branch to the completed child head only after confirming ancestry.**
- [ ] **Step 4: Close PR #277 as superseded by the fast-forwarded parent with an explicit reason.**
- [ ] **Step 5: Resolve the PR #268 cleanup thread only after the integrated exact head demonstrates durable failure recording, retry, metrics, and restart recovery.**
