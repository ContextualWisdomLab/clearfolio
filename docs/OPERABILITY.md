# Clearfolio Operability and Recovery Guide

Status: Canonical operability index
Baseline: protected `main` at `83ec6f7fe2b04bdcd28bf98ec350e41e55730a18`

This guide states current operational truth and links active target changes without inventing SLO/RPO/RTO values that have not been measured.

## Deployment modes

### Standalone

Clearfolio can run as its own Spring Boot/WebFlux service with configured artifact storage and current default job repository. Local filesystem artifact/ledger settings can provide selected restart continuity, but protected-main conversion jobs remain process-local by default.

### Composed MSA

A host such as naruon may call Clearfolio through versioned APIs and provide trusted scoped identity claims. The host does not receive implicit write access to Clearfolio application persistence. Central `.github` workflows are development governance, not runtime infrastructure.

## Startup acceptance

Before routing production-like traffic:

1. application starts with required security configuration;
2. configured cryptographic keys satisfy strength/separation requirements of the integrated revision;
3. artifact store root is app-owned and writable only by intended service identity;
4. local ledgers can be opened/replayed without integrity failure when configured;
5. required converter dependencies for advertised formats are present;
6. health/readiness semantics match the deployed revision;
7. startup does not silently substitute production-required secrets with demo behavior.

## Liveness and readiness

Protected main currently has `/healthz`. `ACTIVE_PR` #295 proposes explicit liveness `/healthz` and readiness `/readyz`. Until that PR integrates, deployment documents must not claim separate probes are shipped.

After integration:

- liveness answers whether the process is restart-eligible;
- readiness answers whether this instance accepts traffic;
- shared dependency failure does not automatically make the process dead;
- responses remain low-information and `no-store`.

## Queue and worker operations

Protected-main worker behavior is bounded and asynchronous. Operators should monitor:

- executor/queue saturation when exposed;
- processing age and stale lease recovery;
- retry count and dead-letter frequency;
- conversion failure class;
- artifact publication success;
- process restart effects on in-memory job state.

`PLANNED`: durable queue depth/lag, cancellation, explicit backpressure and distributed worker lease metrics.

## Artifact operations

- PDF artifacts may live in memory or `FileSystemArtifactStore` depending on configuration.
- Signed artifact links are short-lived and revocable; canonical artifact reads are audited.
- Local filesystem durability is not equivalent to remote object-store transactional guarantees.
- `ACTIVE_PR` #268 adds deletion receipts and restart-safe cleanup evidence; do not rely on those semantics before integration.

## Failure and recovery matrix

| Failure | Current/target behavior | Operator action |
| --- | --- | --- |
| invalid/unsupported/oversized upload | controlled fail-closed rejection | correct source/config; do not bypass validation |
| missing tenant permission | 401/403 or concealed 404 according to boundary | fix upstream authorization, not repository data |
| cross-tenant resource attempt | concealed not-found behavior | investigate caller/tenant routing; no data disclosure |
| conversion transient failure | bounded retry when eligible | observe retry/dead-letter state |
| retry exhaustion | `FAILED` + dead-letter evidence | authorized operator retry only after cause review |
| process restart | startup recovery only for state still represented by current repository | recognize process-local job durability limitation |
| artifact missing/unreadable | controlled artifact failure | verify store ownership/integrity; never fabricate success |
| signed token expired/revoked | fail closed | issue a new authorized link if business policy permits |
| local ledger torn tail/corruption | behavior depends on ledger contract; active deletion work validates crash tails | preserve evidence; recover per ledger-specific runbook |
| readiness false (`ACTIVE_PR`) | remove instance from routing | repair local readiness contributor; avoid restart cascade |
| central CI/reviewer outage | blocks affected integration evidence only | continue safe repository work; do not weaken gates |
| scheduled-task execution failure | blocks only that automation execution until classified | distinguish scheduling/activation from execution failure, preserve writer leases, capture the last safe checkpoint when supported, then continue safe repository work |

## Privacy-safe observability

Current logs/audit/analytics surfaces are incomplete compared with the target OpenTelemetry model. Future telemetry must avoid uncontrolled high-cardinality or sensitive labels such as raw tenant/subject IDs, filenames, paths, approval tokens, artifact tokens, document bytes/digests, and exception-selected values unless an explicitly reviewed evidence purpose requires them.

Recommended low-cardinality metrics include:

- conversion outcome by controlled reason code;
- queue saturation/rejection;
- retry/dead-letter counts;
- readiness state;
- artifact cleanup outcome;
- converter/fidelity profile version where bounded.

No SLO is considered established until measurements from the actual deployment profile are retained and reviewed.

## Scheduled automation incident handling

A **scheduled-task execution failure** is not the same failure boundary as scheduling/activation failure. Operators and autonomous maintainers must classify the boundary before changing code or credentials.

Use this order:

1. Read the live automation definition and determine whether it is enabled, has a valid recurrence, and records a recent invocation. Healthy scheduling/activation evidence proves only that the task was accepted and invoked; it does not prove successful repository execution.
2. If activation is healthy but the returned task outcome is a **generic scheduled-task error**, classify it as an **execution failure** until a more precise failing boundary is observed.
3. Refetch repository state before assuming the failed run was side-effect free. **Before every write, refetch the exact source head and exact target head, the independently resolved live base, the target blob/ref, and the relevant review state: formal reviews, unresolved threads, exact-head checks, and security gates. If the target identity changes or another writer moves the target after that snapshot, reject the stale write, freeze that branch/path, and rotate to another safe item.** Apply the same fail-closed rule before a retry or other recovery mutation.
4. Build separate hypotheses for connector/tool failure, authentication or permission failure, provider/rate/runtime state, repository policy, and prompt-size/duplicated-state amplification. Do not label any hypothesis as root cause without evidence.
5. Treat reviewed **repository authority** as the detailed product/security/architecture source of truth. The recurring scheduler should carry execution control semantics rather than a second copy of the full product specification. Simplifying duplicate scheduler detail must not delete requirements from GitHub or weaken review, test, security, fidelity, writer-lease, or release gates.
6. Apply the smallest reversible feasible control change and verify the automation remains enabled with the intended cadence when the control-plane tool exposes that state.
7. Continue the safe Clearfolio executable queue in the same invocation. Scheduler debt is local and must not become a global excuse to stop product, security, documentation, or PR work.
8. On later runs, re-evaluate whether the failure recurs. Success after a simplification supports the remediation but does not retroactively prove the original internal failure mechanism when detailed telemetry was unavailable.

ADR-0011 owns the thin-scheduler decision. ADR-0008 continues to own central-vs-local automation credentials/authority, while ADR-0009 owns work-conserving RCA and the double-exit termination semantics.

## Scheduler execution receipts and budget continuation (`PLANNED`; issue #331)

Issue #331 and ADR-0012 define the missing diagnosability contract. This is planned architecture, not proof that the external scheduler currently persists structured receipts.

### Required evidence boundaries

A diagnosable run distinguishes:

1. `schedule`: the recurrence became due;
2. `admission`: the scheduler accepted the invocation;
3. `fresh queue`: the run independently resolved current GitHub identities;
4. `atomic action`: one bounded mutation or acceptance operation began;
5. `action receipt`: the operation reached a stable exact evidence boundary;
6. `budget continuation`: the run intentionally stopped before a hard boundary after preserving a verified safe checkpoint;
7. `controlled failure`: an observed failing phase/category was recorded without inventing hidden platform detail;
8. `continuation handoff`: the next run was told what historical evidence exists and that all live state must be refetched.

Activation without execution is not success. A run-start signal without `fresh queue` construction is not repository execution. A generic scheduled-task error is a symptom, not a source defect and not a root-cause verdict.

### Clean budget continuation

Use `budget_continuation` only when actual available tool/runtime signals show that a new RED test, broad refactor, new branch, or multi-step mutation cannot reasonably reach a verified or safely test-only boundary in the current invocation.

Before continuing later:

- finish or safely abandon the current atomic mutation;
- record the **last safe checkpoint** and exact observable action result;
- refetch the affected head/base/ref/blob after any mutation;
- leave no temporary, one-shot, self-modifying, encoded-patch, or competing writer workflow;
- leave no knowingly broken non-test-only source head;
- treat every checkpoint SHA as historical;
- start the next invocation from **fresh GitHub state**.

Budget continuation is not repository completion and must not be used merely because one branch is waiting or because a bounded safe action still fits in the observable budget.

### Controlled failure envelope

Where the platform permits, a privacy-safe failure envelope records:

- opaque run and action identity;
- control/prompt version or digest;
- observed failing phase;
- low-cardinality category such as admission, queue construction, connector/tool, authentication/permission, provider/rate/runtime, repository policy, CAS/publication, verification, or telemetry unavailable;
- last completed action receipt;
- last safe checkpoint;
- branch-local deferred lanes and recovery eligibility.

It excludes raw credentials, authorization values, cookies, approval/artifact tokens, document bytes/content, filenames/paths, tenant/subject/approver identifiers, uncontrolled exception messages, and user-visible stack traces. If detailed telemetry is unavailable, retain `telemetry_unavailable`; **do not invent** a more specific cause.

### Recovery procedure

1. Read the receipt only as historical evidence.
2. Revalidate the external scheduler/control identity where observable.
3. Rebuild the entire executable queue from fresh GitHub state.
4. Recheck writer ownership immediately before each write.
5. Resume at the highest-value safe lane; do not blindly replay the previous action.
6. If a source/ref moved, reject stale publication and freeze only that lane.
7. If Draft PR #271 later implements repository-local receipts, keep its evidence separate from the external scheduler and reacquire all exact-head/base-sensitive gates after its parent dependencies stabilize.

## Incident handling

1. bind incident evidence to exact deployed source/build identity;
2. preserve privacy-safe logs and relevant append-only evidence;
3. identify the first failing component boundary;
4. distinguish product, infrastructure, central CI/governance and dependency-provider ownership;
5. select the smallest reversible repair after feasibility proof;
6. validate in a non-destructive environment where possible;
7. roll forward or back according to data/artifact compatibility;
8. require protected-main/runtime evidence before closing an automation or deployment incident.

## Backup and restore

`PARTIAL`: filesystem artifacts/ledgers can be backed up as files when configured, but this is not yet a complete business-continuity design because default job state remains process-local.

`PLANNED` durable state requires:

- consistent job/state/outbox backup semantics;
- artifact/object-store version alignment;
- tenant-scoped restore;
- generation/idempotency preservation;
- tested restore into an isolated environment;
- measured recovery evidence before publishing RPO/RTO claims.

External scheduler execution receipts remain owned by the executing control plane under ADR-0012. They are not included in Clearfolio application backup scope unless a later accepted ADR explicitly changes ownership.

## Rollback

Code rollback is permitted only when persistence/artifact format and security semantics remain compatible. Never roll back a security boundary merely because a prior release is operationally convenient. Database or durable-ledger migrations require explicit rollback/forward-recovery instructions before release.

Scheduler-receipt rollback must return to fail-closed generic classification and fresh-state reconstruction. It must not restore stale-checkpoint writes, guessed root causes, weaker writer leases, or self-modifying workflows.

## Release operational acceptance

Before release:

- exact integrated protected source is known;
- all required CI/security/coverage/fidelity/accessibility/provenance checks pass;
- deployment starts with release-like security configuration;
- supported-format smoke/fidelity tests run using release artifacts;
- health/readiness behavior matches documentation;
- retry/recovery and artifact access smoke tests pass;
- no production claim depends on demo fixtures or placeholder conversion;
- rollback/recovery constraints are documented;
- published artifact and SBOM/provenance are verified after publication.

## Related runbooks and evidence

- `docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`
- `docs/operations/artifact-deletion-cleanup.md` when the corresponding active PR integrates
- `docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md`
- `docs/analytics/2026-07-02-durable-metrics-event-model.md`
- `docs/engineering/acceptance-criteria.md`
- `docs/qa/evidence/` dated snapshots
- issue #331 and ADR-0012 for scheduler receipt/continuation acceptance

Dated evidence is provenance, not timeless operational truth; this document and protected source define the current contract.
