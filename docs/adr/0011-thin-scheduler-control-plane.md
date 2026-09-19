# ADR-0011: Keep the recurring scheduler as a thin control plane

- Status: Accepted
- Implementation maturity: `PARTIAL` — the external hourly Clearfolio scheduler has adopted the decision; repository-local automation remains `ACTIVE_PR` #271 until protected integration.
- Decision date: 2026-08-10

## Context

The Clearfolio hourly development task was active and recording executions, yet repeated invocations surfaced only a generic scheduled-task failure. The available automation control surface proved that scheduling/activation was healthy, but it did not expose a sufficiently detailed execution log to prove one internal root cause.

The previous scheduler prompt had also grown into a second copy of detailed product, security, documentation, release, and architecture policy. That duplication was a supported failure-amplifier hypothesis rather than a proven single root cause: it increased per-run input size, made stale product detail easier to preserve outside GitHub, and coupled scheduler execution to a large amount of information already maintained in reviewed repository documents.

A scheduler execution failure must not silently convert an execution-first development loop into a one-line status reporter. The scheduler needs enough control policy to recover work, but GitHub must remain the reconstructable source of detailed product truth.

## Decision drivers

- scheduled development must continue after local check/review/provider/scheduler failures when another safe action exists;
- exact product and architecture detail must be reviewable, versioned, testable, and attributable in GitHub;
- repeating that detail in an external scheduler creates two mutable sources of truth;
- scheduler activation state and scheduler execution failure are different diagnostic boundaries;
- loss of detailed scheduler-run telemetry must not justify inventing a root cause;
- the existing writer-lease, exact-head, TDD, governance, and double-exit requirements must survive prompt simplification.

## Considered alternatives

### A. Keep a monolithic, self-contained scheduler prompt

Rejected. It is convenient when repository documents are incomplete, but duplicates reviewed policy, increases stale-state risk, and makes every scheduled invocation carry product detail unrelated to control-plane execution.

### B. Disable the scheduler and rely on manual development

Rejected. It avoids scheduler-specific failures but abandons the required hourly continuation and does not address execution reliability.

### C. Move all control behavior into a repository GitHub Actions writer

Rejected for the current authority model. Central `.github` and repository-local automation have separate writer/reviewer credentials and protection boundaries; moving the external orchestration authority into Actions would require a separate security/governance decision.

### D. Thin control plane with repository-owned detailed authority

Accepted.

## Decision

The recurring Clearfolio scheduler is a **thin control plane**. Repository documents are the detailed authority for product scope, architecture, security, fidelity, data model, testing, operability, research traceability, release acceptance, and current implementation maturity. The scheduler **must not duplicate the full product specification**.

The scheduler retains only execution semantics that cannot safely be delegated away:

1. refetch exact live repository/PR/check/review/writer state at run start;
2. respect the single Clearfolio writer lease and read-only dependency boundaries;
3. maintain a live executable queue and treat waiting as local;
4. apply RCA → distinct remedies → real-world feasibility → action → proof;
5. enforce exact-head review/merge evidence and never manufacture approval;
6. **read current canonical repository documents before selecting or changing product work**;
7. if a prior scheduler execution failure is observed, distinguish scheduling/activation failure from execution failure using the evidence actually available;
8. do not claim an unobservable internal cause as proven;
9. simplify duplicated/stale control state when it is a plausible failure amplifier without weakening safety or acceptance requirements;
10. continue safe repository work in the **same invocation** after scheduler RCA rather than treating scheduler debt as global blocking work; and
11. require the **double exit sweep** before normal termination.

Every source, documentation, ref, PR-state, review-trigger, or merge-adjacent write has an additional fail-closed prewrite contract. **Before every write, refetch the exact source head and exact target head, the independently resolved live base, the target blob/ref, and the relevant review state: formal reviews, unresolved threads, exact-head checks, and security gates. If the target identity changes or another writer moves the target after that snapshot, reject the stale write, freeze that branch/path, and rotate to another safe item.** The write is prepared only against those exact identities.

## Consequences and trade-offs

### Benefits

- detailed product truth remains versioned and reviewed in GitHub;
- each scheduled invocation carries a smaller, more stable control contract;
- prompt simplification can happen without deleting product requirements;
- documentation tests can detect missing authority rather than relying on conversational memory;
- scheduler failures become an operability problem with a bounded recovery procedure instead of a routine status message.

### Costs

- the scheduler must read the relevant repository authority before acting;
- a missing/stale canonical document becomes an actionable repository defect;
- external automation state and repository docs are two evidence systems and must be linked explicitly in traceability rather than conflated.

## Failure and recovery behavior

When a scheduled task fails:

1. read live automation state and last-run evidence available to the actor;
2. classify whether the task failed to schedule/activate or failed during execution;
3. inspect repository/GitHub state to determine whether the prior run mutated any target before failing;
4. before any recovery write, refetch current source head, independently resolved current base, formal reviews, unresolved threads, exact-head checks, security gates, target blob/ref, and writer lease; if any target identity moved, reject the stale operation, freeze that lane, and rotate;
5. test separate hypotheses such as prompt-size and duplicated-state amplification, connector/tool failure, authentication/authorization, GitHub provider/rate state, or repository policy;
6. execute the smallest reversible supported remedy;
7. continue the repository executable queue in the same invocation when safe;
8. record durable new operating decisions in canonical docs; and
9. require fresh exact-head evidence after any repository mutation.

If detailed scheduler execution telemetry is unavailable, record the internal cause as unresolved rather than guessing. Activation evidence alone cannot prove execution success.

## Security, privacy, and governance impact

- no new repository token or model secret is introduced by this decision;
- scheduler prompt reduction must not weaken branch protection, independent review, exact-head checks, tenant boundaries, model credential isolation, or writer leases;
- repository authority must not include secrets; secrets remain GitHub Secret/runtime configuration material;
- central `.github`, naruon, contextual-orchestrator, and repositories with dedicated writer loops remain read-only to this scheduler.

## Compatibility and migration

Existing detailed scheduler clauses migrate into the corresponding canonical GitHub documents instead of being deleted as requirements. The scheduler may reference those files by path and maturity. If an applicable canonical document has not yet reached protected `main`, an active-PR version may be read only as `ACTIVE_PR` evidence and must not be represented as shipped behavior.

Repository-local OpenCode automation in #271 is compatible with this decision but does not become protected-main behavior until its own dependencies, reviews, checks, and central `.github` integration satisfy governance.

## Tests and acceptance evidence

Acceptance requires:

- `scripts/test_release_loop_adr_contract.py` to require this ADR and the scheduled-task runbook terms in `docs/OPERABILITY.md`;
- the same contract to prove the scheduler reads current repository authority before product selection, and to verify the complete prewrite refetch/freeze behavior plus Markdown trailing-newline discipline;
- the ADR index and `docs/TRACEABILITY.md` to link this decision;
- scheduler control state to remain enabled with the thin prompt after update;
- a future scheduled execution to distinguish scheduler activation from execution failure instead of emitting only a generic failure message where the control surface allows that distinction;
- no regression in the work-conserving double-exit, exact-head, writer-lease, TDD, approval, or release gates.

A successful prompt update is not proof that all future scheduler failures are solved; it proves only that the duplicated-control-state failure amplifier has been removed and the recovery contract is explicit.

## Rollback and supersession

Rollback to a monolithic prompt is allowed only if evidence shows repository-authority lookup itself is the material reliability failure and a bounded alternative cannot preserve execution requirements. Such a change requires a new ADR because it would reintroduce duplicated detailed authority.

Supersede this ADR if the product later adopts a repository-native orchestration control plane with equivalent writer isolation, scheduler-failure telemetry, exact-head/live-base evidence, and work-conserving termination semantics.
