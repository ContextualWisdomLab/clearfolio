# ADR-0004: Fence durable job/artifact deletion by immutable lifecycle generation

Status: Proposed
Implementation maturity: `ACTIVE_PR` #268

## Context and drivers

Deletion and retry span repository metadata, artifact bytes and process restart. A job UUID alone is unsafe if stale work can act on a later replacement or if a failed artifact read is misclassified as confirmed absence. Process-local recovery cursors cannot provide durable fairness after restart.

## Alternatives

1. Best-effort delete artifact then metadata with process-memory retries.
2. Use the UUID alone as the deletion identity.
3. Permanently reserve identity, bind operations to tenant + immutable lifecycle generation, and persist a deletion receipt before artifact inspection.

## Decision

Choose alternative 3. Durable mutation uses tenant/job/generation fences. A deletion receipt is persisted before the first artifact read, advances only through validated transitions, binds the exact artifact digest or explicit confirmed-absence state before metadata tombstoning, and records retryable controlled failure evidence. Recovery ordering is reconstructed from durable transition times/evidence rather than a process-local cursor.

## Consequences

Lifecycle logic and append-only validation become more complex, but stale work, UUID rebinding, restart starvation and ambiguous deletion outcomes become detectable and fail closed.

## Failure and recovery

Unreadable artifact bytes are not confirmed absence. Failed attempts retain metadata and append controlled retry evidence. Torn ledger tails, invalid UTF-8, identity conflicts, fabricated initial records, illegal successor states and inconsistent attempt evidence are rejected or handled under documented crash-tail rules.

## Security and privacy

Deletion evidence avoids raw sensitive content and exception-selected log data. Generation fences protect cross-tenant/cross-lifecycle integrity as well as availability.

## Compatibility and migration

Standalone local ledgers remain valid deployment choices. Distributed SQL/object-store adapters must implement equivalent atomicity/fencing or explicitly reject unsupported operations. Existing identifiers are not recycled.

## Tests and acceptance

Crash/restart replay, permanently failing oldest receipt, bounded recovery fairness, generation mismatch, duplicate identity, initial-read failure, cleanup failure, torn-tail validation, privacy logging, concurrency serialization, and **exact owned production statement/branch coverage with JaCoCo line-missed = 0 and branch-missed = 0** for the lifecycle code introduced by the implementing change.

Coverage is structural evidence only; the lifecycle acceptance tests above must prove the state, concurrency, recovery and tenant-generation invariants independently.

## Rollback / supersession

Do not roll back to UUID reuse or process-only fairness. A successor distributed transaction/outbox design may supersede this ADR only after equivalent generation, idempotency and recovery guarantees are proven.
