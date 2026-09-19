# ADR-0012: Scheduler execution receipts and resumable continuation

Status: Proposed
Implementation maturity: `PLANNED`; issue #331

## Context

Clearfolio uses an external recurring scheduler as a thin execution control plane and is also developing a repository-local OpenCode product-development workflow in Draft PR #271. These are separate execution authorities. The external scheduler has repeatedly returned only the generic scheduled-task error `There was a problem with your scheduled task.` while the repository still contained executable work.

The available control-plane interface does not expose an exception type, failing phase, action receipt, stack trace, or durable checkpoint. Therefore maintainers must **do not invent** a hidden root cause such as prompt length, provider failure, connector failure, permissions, or hard runtime exhaustion without evidence. Scheduler activation, repository execution, exact GitHub mutation, clean budget continuation, and failure are different evidence classes.

Issue #331 records the operational gap: a scheduled invocation needs privacy-safe evidence showing how far it progressed, what atomic action was last proven, whether a continuation is safe, and which state must be refetched next.

## Decision drivers

- Diagnose generic scheduler failures without fabricating platform internals.
- Resume work after practical tool or runtime exhaustion without trusting stale SHAs.
- Prevent half-published changes, temporary writer machinery, and knowingly broken non-test-only heads.
- Keep the scheduler prompt thin while GitHub documents remain detailed product authority.
- Preserve the single Clearfolio writer lease and branch-local freeze-and-rotate behavior.
- Avoid persisting secrets, document content, tenant/subject identifiers, tokens, or uncontrolled exception text.
- Distinguish the external scheduler from Draft PR #271 and from central `.github` PR maintenance.

## Considered alternatives

### Alternative A — Keep only the generic task error

Rejected. A generic sentence does not distinguish schedule admission, execution start, queue construction, repository mutation, controlled deferment, budget continuation, or failure. It also encourages repeated speculative prompt edits instead of evidence-backed RCA.

### Alternative B — Store all scheduler state in the Clearfolio application database

Rejected. The external platform owns the scheduler runtime. Clearfolio must not invent a physical database or pretend it owns platform execution persistence. A future repository-local workflow may persist GitHub-owned evidence, but that is a separate reviewed implementation decision.

### Alternative C — Versioned execution receipts owned by the executing control plane

Accepted. The executing scheduler or workflow owns durable receipt storage where supported. Clearfolio owns the semantic contract, privacy constraints, traceability, and tests. The repository models these records as conceptual/external evidence rather than shipped application tables.

## Decision

Every autonomous development invocation should expose or persist, where the executing platform permits, a versioned execution receipt composed of conceptual records. An **automation checkpoint** is the human-readable concept represented by `automation_checkpoint`: the last safe execution phase plus exact historical evidence identity.

- `automation_run`: immutable run identity and scheduler/control version or digest;
- `automation_checkpoint`: the last safe phase and exact historical evidence identity;
- `queue_snapshot`: protected-main, PR, issue, dependency, review, check, and writer-lease identities observed when the queue was built;
- `deferred_lane`: one locally blocked lane keyed by exact PR/head/live-base/run/review identity and defer reason;
- `action_receipt`: the last completed atomic repository action and its observable proof;
- `failure_envelope`: controlled failing phase/category and recovery eligibility;
- `continuation_handoff`: next-run context that is explicitly historical and requires a fresh GitHub refetch.

The lifecycle distinguishes at least:

```text
schedule
→ admission
→ fresh queue
→ atomic action
→ action receipt
→ next action
→ completed | budget continuation | failure envelope
```

### Phase semantics

1. `schedule` proves only that recurrence was due.
2. `admission` proves only that the platform accepted the invocation.
3. `fresh queue` records the GitHub identities used for selection; those identities become historical after any movement.
4. `atomic action` identifies one mutation or acceptance operation that can reach a stable proof boundary.
5. `action receipt` binds the operation to an exact source/ref/blob/PR/issue/check identity where observable.
6. `budget continuation` is a clean non-completion state used only when actual runtime/tool signals indicate that starting another multi-step mutation cannot reasonably reach a verified boundary.
7. `failure envelope` records only an observed controlled category and phase. It does not substitute speculation for missing telemetry.

### Budget continuation

Before a hard invocation boundary, the runner should finish or safely abandon the current atomic action, refetch the resulting exact state, remove temporary writer machinery, and avoid leaving a knowingly broken non-test-only source head. The next invocation starts from a full fresh queue. A budget continuation is not repository completion and does not waive normal double-exit-sweep semantics when sufficient budget remains.

### Fresh-state rule

A continuation handoff never authorizes writes by itself. The next run must independently refetch protected main, every open PR/issue, exact heads/live bases, reviews/threads, checks/security evidence, target blobs/refs, dependencies, and active-writer state before any decision or mutation.

### Privacy and security

Receipts must not contain:

- raw credentials, API keys, authorization headers, cookies, approval tokens, or artifact tokens;
- document bytes, extracted document content, filenames/paths, or uncontrolled digests without an approved evidence purpose;
- raw tenant, subject, approver, or operator identifiers;
- provider-controlled exception messages or stack traces in user-visible receipts;
- model prompt content that can contain untrusted repository/document text.

Use controlled phase codes, low-cardinality reason codes, opaque run/action identifiers, and exact Git object identities only where needed for repository evidence. Pseudonymized values remain personal data and retain purpose/retention controls.

### Authority separation

- The external scheduler remains a thin control plane.
- Reviewed repository documents, current source, and live GitHub policy remain detailed authority.
- Draft PR #271 is future repository-local automation and must not be presented as proof that the external scheduler executed successfully.
- Central `.github` retains its separately reviewed PR-maintenance authority.
- No receipt authorizes self-approval, gate bypass, release, deployment, or a competing writer.

## Consequences

### Positive

- Operators can distinguish activation from execution and clean continuation from failure.
- A recurring run can resume without trusting stale source/base/check/review evidence.
- Generic task errors become an observable control gap rather than a guessed root cause.
- Privacy-safe action lineage improves incident review, acquisition diligence, and scheduler reliability evidence.
- The design preserves the thin prompt and one canonical GitHub documentation graph.

### Negative

- The executing platform must expose or store additional structured evidence.
- Receipt schemas and retention require versioning and compatibility management.
- External platforms may not expose enough telemetry for full implementation; in that case the receipt must explicitly record `unavailable` evidence rather than inventing detail.
- More exact identities increase traceability volume and require bounded retention.

## Failure and recovery

- If no run identity is available, classify the observation as schedule/control-plane evidence only and continue repository work through another safe lane when tools remain.
- If admission succeeds but queue construction does not, record the failing phase without claiming repository execution.
- If a repository action fails before mutation, record the exact attempted boundary and no-mutation evidence where observable.
- If contents/ref publication races another writer, reject stale CAS, freeze only that branch, and rotate.
- If a test-only RED head is published, the same run should reach GREEN when feasible; otherwise the handoff must explicitly identify a safe test-only continuation and must not represent it as mergeable product code.
- If a platform cannot expose detailed failure telemetry, preserve the generic scheduled-task error as the symptom and **do not invent** the underlying cause.
- Recovery always starts from fresh GitHub state, not from the checkpoint SHA as current authority.

## Security, privacy, and governance impact

The receipt model strengthens least privilege and evidence separation. It does not expand scheduler credentials, review authority, branch protection, release authority, model access, or cross-repository write scope. Retention and access to receipts must be purpose-bound and auditable. Repository-local autonomous model use continues to require OpenCode and GitHub Secret `NVIDIA_NIM_API_KEY`; `COPILOT_GITHUB_TOKEN` is not a development-model credential.

## Compatibility and migration

- Existing external runs without receipts remain historical `telemetry_unavailable` evidence.
- New receipt versions must remain parseable or explicitly migrated; unknown fields must not grant authority.
- Draft PR #271 may adopt the semantic model after #270 and central prerequisites stabilize, but predecessor checks/reviews do not transfer.
- No Clearfolio application database migration is implied by this ADR.

## Tests and acceptance

Issue #331 owns implementation acceptance. Machine-checkable repository documentation must verify:

- activation without execution is distinguishable;
- run start without queue construction is not success;
- the generic scheduled-task error maps to a controlled failing phase without invented cause;
- hard-budget simulation produces `budget continuation`, not completion;
- partial publication is bound to exact parent/blob/ref identity;
- writer-lease conflict is branch-local;
- the next run refetches live GitHub identities;
- receipts exclude raw secrets, tokens, document content, tenant/subject identifiers, and uncontrolled exception text;
- external scheduler receipts and Draft PR #271 are distinct authorities.

Repository documents must keep ADR, data model, UML, operability, traceability, documentation assessment, and changelog aligned.

## Rollback

A receipt implementation may be disabled if it leaks sensitive data, blocks safe execution, or cannot preserve exact evidence identity. Rollback returns to fail-closed generic classification and fresh-state reconstruction; it must not restore invented root causes, stale checkpoint writes, self-modifying workflows, or weaker writer leases.

## Supersession

Supersession requires a later ADR that identifies a demonstrably stronger execution-evidence mechanism, preserves activation/execution/evidence-authority separation, defines migration and rollback, and keeps privacy-safe resumable continuation. A platform-native durable run ledger may supersede this conceptual contract only after equivalent acceptance evidence exists.
