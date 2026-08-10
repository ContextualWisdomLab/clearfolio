# ADR-0008: Separate central PR-maintenance authority from local product-development authority

Status: Proposed
Implementation maturity: central dependency plus `ACTIVE_PR` #271

## Context and drivers

PR review/repair/merge automation needs privileged organization governance, while buyer-gap development benefits from repository-specific context. Copying privileged central workflows into each product creates duplicated policy, competing writers, excess credentials and inconsistent review semantics.

## Alternatives

1. Give one local scheduler authority to develop, review, approve and merge.
2. Copy the central maintenance implementation into Clearfolio.
3. Keep privileged PR maintenance in `ContextualWisdomLab/.github` and allow a bounded Clearfolio-local product agent to propose only path-disjoint Draft work.

## Decision

Choose alternative 3. Central PR-maintenance automation owns organization-governed review dispatch, gate evaluation and protected merge semantics; it does not grant a general fleet product-development writer a second Clearfolio source lease.

The dedicated Clearfolio-local OpenCode development loop is the **only actor permitted to process `ACTIVE_PR` product-development source mutations** while it holds the Clearfolio writer lease. General **central fleet loops keep Clearfolio disabled** for product-development writes and must not race that lease. Central automation may perform read-only review and gate evaluation, and may merge commits produced by the leased local writer when the merge path itself satisfies repository governance. A central repair that mutates Clearfolio source is permitted only after the local source writer is released or fenced and the central action successfully acquires the **same Clearfolio writer lease** before mutation. Without that lease transfer, central automation is not a source-repair writer.

Lease transfer is a state transition, not a timing convention: the central action must refetch current head/base, prove the prior writer is fenced or released, acquire the same lease identity, and revalidate the target blob/ref before its first mutation. If any of those preconditions cannot be proven, it remains read-only for Clearfolio. This aligns central repair authority with ADR-0009's branch-local active-writer freeze instead of creating a repair exception that can overlap the local writer.

The local OpenCode execution path is supply-chain constrained: its agent/action source is **immutable and pinned** to a reviewed full commit/digest rather than a floating branch or tag; dependency/bootstrap artifacts are integrity-verified and fail closed when provenance cannot be established. The local agent may diagnose, test and propose bounded changes, but does not self-approve, weaken protection, merge, release or deploy. Independent reviewer credentials and authorities stay separate from proposal credentials.

## Consequences

Central defects are fixed centrally rather than hidden by leaf workarounds. Local product development can continue during unrelated approval/check latency without receiving merge authority. A single product-development writer lease prevents duplicate mutation while retaining organization-level review/merge governance. Central source repair has an explicit lease-handoff cost rather than an implicit privileged bypass.

## Failure and recovery

If central maintenance is unavailable, local product work may continue only within its safe proposal/verification boundary. If the local agent lacks model/publication credentials, cannot verify its pinned agent/dependency provenance, or detects path/ref ownership conflict, it fails closed for that action and rotates to another safe task rather than broadening credentials. If a competing writer is detected, the affected branch is frozen for the run and refetched later. A failed central lease handoff leaves central maintenance read-only; it does not authorize a best-effort competing repair.

## Security and privacy

Model-backed local development uses GitHub Secret `NVIDIA_NIM_API_KEY` only at the model step, never `COPILOT_GITHUB_TOKEN` as a development-model credential. Credential-bearing proposal execution is separated from credential-free verification and short-lived scoped publication authority. Floating agent/action references and blanket secret inheritance are not accepted substitutes for reviewed immutable sourcing and explicit secret contracts.

## Compatibility and migration

Leaf callers/contracts may evolve with the central reusable workflow, but central implementation remains the PR-maintenance authority. Reviewer credential names/scopes are not casually changed by product feature work. Enabling another Clearfolio product-development writer requires an explicit successor ADR that replaces, rather than silently overlaps, this single-writer lease.

## Tests and acceptance

Scheduler contract tests; Clearfolio-disabled assertions for general fleet product-development loops; path-overlap and protected-path refusal; immutable pinned agent/action and patch/source checks; credential-free full verification; short-lived scoped publication; live writer-lease refetch before mutation; negative test proving central source mutation is rejected while the local writer lease remains active; handoff test proving central mutation is accepted only after release/fencing plus acquisition of the same lease and a fresh head/base/blob revalidation; central protected-main operational acceptance before claiming central repair closure.

## Rollback / supersession

Disable the local proposal loop without affecting ordinary product runtime. Supersede only if a reviewed control-plane architecture preserves writer separation, reviewer independence, immutable supply-chain sourcing and least privilege.
