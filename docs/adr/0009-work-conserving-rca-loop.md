# ADR-0009: Make autonomous development work-conserving and action-oriented

Status: Proposed
Implementation maturity: scheduler/prompt policy in progress; `ACTIVE_PR` #271 contains repository workflow contract

## Context and drivers

A recurring automation failure mode is to diagnose one blocker, report it, and terminate while unrelated safe work remains. Review latency, queued checks, missing approval, a failed first remedy, a documentation update, or a single completed PR is not a repository outcome that justifies idling the rest of the invocation. A blocker report or plan alone is not a successful run when another safe executable item remains.

## Alternatives

1. Stop after the first blocker or notable result and rely on the next hourly recurrence.
2. Retry the same blocked action repeatedly until it changes.
3. Maintain a live executable queue and use RCA → distinct remedies → empirical feasibility → same-run action → proof, then immediately rotate to the next safe item.

## Decision

Choose alternative 3. The hourly recurrence is continuation after real invocation-budget exhaustion, not a reason to stop voluntarily. A blocked action is deferred by exact identity while the loop works elsewhere. Every successful mutation, proof, merge, closure, documentation repair, or defer decision is followed by another queue selection. Two fresh exit sweeps are required before a normal run may terminate for lack of work.

## Feasibility contract

For a non-passing state the loop must:

1. identify the first failing boundary and correction owner;
2. separate symptom, immediate cause, root cause and systemic/control cause where material;
3. enumerate materially distinct bounded remedies;
4. verify actual tool/API support, permissions, credentials, reviewer eligibility, branch/ruleset semantics, dependency ownership, writer lease, runtime/rate-limit state, blast radius, rollback and an exact acceptance test;
5. reject invented authority/secrets/reviewers, weakened gates, stale evidence and duplicate writers;
6. execute the smallest safe root-cause-changing remedy test-first;
7. treat a failed/no-op attempt as new evidence and either try another distinct remedy or rotate.

## Executable exit-sweep protocol

Each fresh exit sweep is a new evidence pass, not a replay of cached blocker state. It must:

1. refetch live head and live base for every still-relevant PR or branch;
2. refetch current reviews, commit statuses, model verdicts, check runs, workflow evidence, required checks, issue/PR state and protected-main state needed to rebuild the executable queue;
3. bind evidence to the current exact PR/head/base/run/review identity, carry forward deferred identities only as exact PR/head/base/run/review references, invalidate evidence not bound to the current exact identity, and then revalidate whether the reason for deferral still exists;
4. revalidate the writer lease before any newly executable mutation;
5. execute any safe item discovered by the sweep and restart the exit-sweep count after that action.

If a safe item appears between sweep one and sweep two, the loop must not terminate. It must execute or safely defer that item using fresh evidence, then begin the two-sweep exit proof again. Only two consecutive fresh sweeps with no safe executable work establish the normal no-work exit condition.

## Consequences

Runs use more of their practical execution budget and produce fewer status-only outcomes. The scheduler needs stronger branch/path ownership checks and a deferred-work set to avoid races and polling loops.

## Failure and recovery

When one branch has another active writer, freeze only that branch for the run. When no model/publication credential exists, mark that specific mutation infeasible rather than inventing a credential. When all remaining work is genuinely external-only or unsafe, the exit sweep records that boundary and the next hourly run refetches from scratch.

## Security and governance

Work conservation never means bypassing protection, self-approval, force-push, weakening tests, racing writers, or expanding credential scope. Safety and repository policy dominate throughput.

## Tests and acceptance

- scheduler contract tests for RCA, feasibility and same-run action;
- path-disjoint ownership and protected-path refusal;
- queue-capacity and credential fail-closed tests;
- exact base/head/patch rechecks before and after publication;
- stale-evidence regressions for every ADR-0007 authority class: current reviews, commit statuses, model verdicts, check runs, workflow evidence and required-check interpretations must be rejected when their exact PR/head/base/run/review identity no longer matches;
- regression coverage that discovering a new source head or live base resets the affected evidence and queue decision rather than inheriting predecessor evidence;
- regression test that a blocker report/plan alone is not an accepted product outcome when a safe item remains;
- final double-exit-sweep policy in the active scheduler prompt;
- contract coverage for the executable fresh-sweep protocol and its reset-on-new-work rule.

## Rollback / supersession

The loop can be disabled without changing product runtime. Supersede only with an automation policy that preserves work conservation, writer safety, feasibility proof and no-report-as-completion semantics.
