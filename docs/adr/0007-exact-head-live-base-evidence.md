# ADR-0007: Bind merge evidence to exact source head and current live base

Status: Proposed
Implementation maturity: policy accepted; CI enforcement strengthened in `ACTIVE_PR` #270

## Context and drivers

PR bodies, remembered SHAs, earlier workflow runs and GitHub PR base snapshots can become stale while branches move. Stacked PRs amplify this risk. A predecessor-head green check cannot establish current-head correctness, and a synthetic merge result does not replace source-head evidence.

## Alternatives

1. Trust the latest PR body/check narrative.
2. Trust any successful run on the same PR number.
3. Refetch the exact source head and independently resolve the current base-ref tip; classify every check/review/status by the revision it actually covers.

## Decision

Choose alternative 3. Every review/fix/merge cycle begins from live source-head and live-base identity. Required source behavior is verified on exact head; base-sensitive compatibility is verified separately. Any relevant head/base movement invalidates predecessor assumptions and triggers revalidation.

A protected merge has two atomic identity preconditions at merge time: the **expected source-head SHA** and the **supported base-tip SHA**. A merge path is compliant only when the merge operation atomically binds or compares both exact values, for example through a compare-and-swap-equivalent integration primitive. If either exact SHA differs from the values used for the final acceptance decision, the operation must reject the stale attempt and require fresh-state revalidation from newly fetched head/base/review/check state. A platform or tool path that can bind only the source head, or that relies on live-base refetch plus ancestry/ruleset checks without exact base-tip equality at the merge boundary, is not an authorized merge path under this ADR and must be rejected rather than treated as sufficiently atomic.

This requirement deliberately separates read-side preparation from write authority. A final refetch can reduce uncertainty, but it cannot close the refetch-to-merge TOCTOU window by itself. If the available merge interface cannot enforce both exact identity preconditions, automation may continue read-only review, validation, and preparation while another governed integration mechanism is selected; it must not infer atomicity from timing proximity.

## Consequences

More reruns may be necessary after branch movement, but stale evidence cannot silently authorize integration. PR prose becomes narrative rather than authority. Some otherwise convenient merge APIs may be unusable until the repository has an exact-head/exact-base compare-and-swap-equivalent integration path.

## Failure and recovery

Queued, pending, skipped-required, cancelled, absent, stale-head, predecessor-head, synthetic-only, status-only and failed evidence is non-passing. If a branch moves during a write or refetch-to-merge operation, the writer freezes that attempt and reconciles from fresh state instead of racing. Lack of a two-SHA atomic merge primitive is a merge-path capability gap, not permission to weaken the precondition.

## Security and governance

Formal GitHub review, commit status, model verdict, check run and workflow evidence remain separate authority classes. COMMENTED/model/status evidence never becomes independent human approval by wording alone.

## Compatibility and migration

Stacked branches must be reconciled after their parent integrates or moves materially. No review/check evidence transfers automatically to a replacement head.

## Tests and acceptance

Workflow contract tests assert job-scoped exact-head checkout and synthetic-merge behavior; pre-merge logic verifies current head/base; exact-head CI/security/fuzz and live review state are refetched immediately before integration. Merge-control acceptance must also cover the refetch-to-merge race: after the candidate snapshot, move either the exact source-head SHA or the exact supported base-tip SHA and prove the merge operation rejects the stale attempt because exact SHA equality no longer holds. An ancestry-compatible but different base tip must still be rejected. The next attempt begins only after fresh-state revalidation and reacquisition of evidence affected by the changed identity.

## Rollback / supersession

Do not roll back to PR-number-based evidence reuse or refetch-plus-ancestry pseudo-atomicity. A successor may optimize evidence caching only if cryptographically/revision-bound invalidation preserves the same correctness properties and the final merge operation still binds both exact identities atomically.
