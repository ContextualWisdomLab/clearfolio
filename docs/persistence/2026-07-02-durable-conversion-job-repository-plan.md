# Durable Conversion Job Repository Migration Plan

Date: 2026-07-02

This document closes the current durable-persistence design artifact for buyer
diligence. It does not implement a production database yet. The current runtime
still uses `InMemoryConversionJobRepository`, while `ConversionJobRepository`
provides the boundary that a durable implementation must satisfy.

## Current State

Current implementation:

- `ConversionJobRepository` exposes `save`, `findById`,
  `findByTenantAndContentHash`, `findAll`, and atomic
  `findOrStoreByContentHash`.
- `InMemoryConversionJobRepository` stores jobs in process memory and dedupes
  by `tenantId + contentHash`.
- `DefaultDocumentConversionService` creates a mutable `ConversionJob` and
  enqueues work only when the repository reports a new canonical job.
- `DefaultConversionWorker` mutates job state through `markProcessing`,
  `markSucceeded`, `markRetryScheduled`, and `markDeadLettered`.
- `retryDeadLettered` mutates a dead-lettered job back to `SUBMITTED`, calls
  `repository.save(job)`, and re-enqueues it.

Buyer risk:

- process restart loses conversion jobs;
- conversion status is not recoverable after worker crash;
- retry schedule is not durable;
- status changes made inside the mutable domain object are not persisted through
  a repository call today, except operator retry;
- analytics and audit evidence can only be partial until lifecycle events are
  durable.

## Design Decision

Do not add a database dependency or submodule in this slice. The first durable
implementation should stay in the main application and preserve the current
`ConversionJobRepository` boundary. A separate library or Git submodule is not
justified until a second service or SDK consumes the same persistence contract.

The durable design should use two persistence concepts:

1. `conversion_jobs`: current authoritative job state for API reads and worker
   claim checks.
2. `conversion_job_events`: append-only lifecycle trail for recovery, audit,
   analytics backfill, and buyer diligence.

This avoids the unsafe shortcut of only snapshotting mutable `ConversionJob`
objects. Every business transition must persist both the current state and the
event that explains why the state changed.

## Target Tables

```sql
CREATE TABLE conversion_jobs (
  job_id UUID PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  original_file_name TEXT,
  content_type TEXT,
  content_hash TEXT,
  file_size_bytes BIGINT NOT NULL,
  status TEXT NOT NULL,
  status_message TEXT,
  converted_resource_path TEXT,
  attempt_count INTEGER NOT NULL,
  max_attempts INTEGER NOT NULL,
  retry_at TIMESTAMPTZ,
  dead_lettered BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  version BIGINT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_conversion_jobs_tenant_hash
  ON conversion_jobs (tenant_id, content_hash)
  WHERE content_hash IS NOT NULL;

CREATE INDEX idx_conversion_jobs_tenant_status
  ON conversion_jobs (tenant_id, status, updated_at);

CREATE INDEX idx_conversion_jobs_retry_due
  ON conversion_jobs (status, retry_at)
  WHERE status = 'SUBMITTED' AND retry_at IS NOT NULL AND dead_lettered = false;

CREATE TABLE conversion_job_events (
  event_id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES conversion_jobs (job_id),
  tenant_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  event_version INTEGER NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  actor_id TEXT,
  status_before TEXT,
  status_after TEXT,
  attempt_count INTEGER,
  retry_at TIMESTAMPTZ,
  payload JSONB NOT NULL
);

CREATE INDEX idx_conversion_job_events_job_time
  ON conversion_job_events (job_id, occurred_at);

CREATE INDEX idx_conversion_job_events_tenant_time
  ON conversion_job_events (tenant_id, occurred_at);
```

## Transition Contract

| Current method | Durable transition event | State update requirement |
| --- | --- | --- |
| Job creation in `DefaultDocumentConversionService.submit` | `conversion.job.submitted` | Insert `SUBMITTED` job and event in one transaction. |
| `findOrStoreByContentHash` dedupe hit | `conversion.job.dedupe_hit` | Return canonical job without enqueuing duplicate work. |
| `ConversionJob.markProcessing` | `conversion.processing.started` | Atomically claim `SUBMITTED` row, increment attempt, clear `retry_at`. |
| `ConversionJob.markRetryScheduled` | `conversion.retry.scheduled` | Persist `SUBMITTED`, retry time, cleared processing timestamps. |
| `ConversionJob.markSucceeded` | `conversion.job.succeeded` | Persist `SUCCEEDED`, artifact path, completion time. |
| `ConversionJob.markDeadLettered` | `conversion.job.failed` | Persist `FAILED`, `dead_lettered=true`, completion time. |
| `retryDeadLetteredToSubmitted` | `conversion.retry.accepted` | Reset attempts, clear artifact path, persist re-queued state. |

The durable repository must not rely on callers remembering to call `save`
after mutating a job. The worker should call explicit transition methods or a
transactional state service that both mutates and persists.

## Repository API Changes

Keep current API for compatibility:

- `save`
- `findById`
- `findByTenantAndContentHash`
- `findAll`
- `findOrStoreByContentHash`

Add a durable transition service before adding a SQL implementation:

```java
interface ConversionJobStateStore {
    Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now);
    void scheduleRetry(UUID jobId, String message, Instant retryAt);
    void markSucceeded(UUID jobId, String resourcePath, String message);
    void markDeadLettered(UUID jobId, String message);
    boolean retryDeadLettered(UUID jobId, String operatorId);
}
```

This keeps mutation persistence explicit and lets the in-memory implementation
remain the fast test baseline while a SQL implementation is introduced behind
the same service contract.

## Worker Recovery Model

Target worker behavior:

1. API inserts a durable `SUBMITTED` job and emits `conversion.job.submitted`.
2. Worker claims a due job with optimistic version check or `FOR UPDATE SKIP
   LOCKED`.
3. Worker persists `PROCESSING` before conversion starts.
4. Worker writes artifact bytes to the durable artifact store.
5. Worker persists `SUCCEEDED` only after artifact write succeeds.
6. Failure persists either `SUBMITTED` with `retry_at` or `FAILED` with
   `dead_lettered=true`.
7. Restart scans `SUBMITTED` jobs with no future `retry_at` and stale
   `PROCESSING` jobs older than a configured lease.

Stale `PROCESSING` recovery should not immediately mark jobs failed. First
implementation should requeue them once with a `worker_lease_expired` event,
then use the normal max-attempt/dead-letter policy.

## Migration Sequence

1. Add `ConversionJobStateStore` with in-memory implementation and tests.
2. Refactor `DefaultConversionWorker` to use explicit transition methods.
3. Keep `ConversionJobRepository` read methods stable for controllers and KPI
   snapshots.
4. Add SQL schema migration scripts and a disabled SQL repository profile.
5. Add repository contract tests that run against in-memory and SQL
   implementations.
6. Add restart recovery tests for due retries and stale `PROCESSING` jobs.
7. Add event emission for lifecycle transitions and map it to the durable
   metrics event model.
8. Turn on SQL profile only in a buyer sandbox after artifact store persistence
   and OIDC/gateway claims are configured.

## Buyer Acceptance Criteria

- Restart does not lose submitted, processing, succeeded, failed, or
  dead-lettered job state.
- Duplicate uploads dedupe by tenant and content hash with a database uniqueness
  guarantee.
- Every lifecycle transition has an append-only event.
- Worker retry and stale processing recovery are deterministic and tested.
- KPI projections can rebuild from lifecycle events.
- Cross-tenant reads still return `404` or filtered aggregates.
- No raw document bytes, approval tokens, signed artifact tokens, or converter
  stdout/stderr are stored in lifecycle events.

## Open Risks

- Legal approval for review-required dependencies remains external.
- Production OIDC/JWT validation remains external to this persistence plan.
- Durable artifact store selection must be finalized before SQL job persistence
  can claim full production recovery.
- Implementing a SQL repository without refactoring mutable state transitions
  would create buyer-visible inconsistency risk.
