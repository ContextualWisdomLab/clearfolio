# Durable Conversion Job Repository Migration Plan

Date: 2026-07-02

This document closes the current durable-persistence design artifact for buyer
diligence. It does not implement a production database yet. The current runtime
still uses `InMemoryConversionJobRepository`, while `ConversionJobRepository`
provides the read and dedupe boundary and `ConversionJobStateStore` provides the
explicit lifecycle transition boundary that a durable implementation must
satisfy.

## Current State

Current implementation:

- `ConversionJobRepository` exposes `save`, `findById`,
  `findByTenantAndContentHash`, `findAll`, and atomic
  `findOrStoreByContentHash`.
- `InMemoryConversionJobRepository` stores jobs in process memory and dedupes
  by `tenantId + contentHash`.
- `ConversionJobStateStore` exists in code and exposes explicit transition
  methods for processing claims, retry scheduling, success, dead-lettering, and
  operator retry acceptance.
- `ConversionJobLifecycleEvent` now records a process-local append-only trail
  for job submission, dedupe hit, processing start, retry scheduling, success,
  failure, and operator retry acceptance without storing source filenames,
  content hashes, artifact paths, raw document bytes, signed tokens, or raw
  converter error strings.
- `DefaultDocumentConversionService` creates a mutable `ConversionJob` and
  enqueues work only when the repository reports a new canonical job.
- `DefaultConversionWorker` routes lifecycle changes through
  `ConversionJobStateStore` instead of directly mutating job lifecycle state.
- `retryDeadLettered` routes operator retry acceptance through
  `ConversionJobStateStore` and re-enqueues only after the transition succeeds.

Buyer risk:

- process restart loses conversion jobs;
- conversion status is not recoverable after worker crash;
- retry schedule is not durable;
- lifecycle status changes now have an explicit code boundary, but the current
  implementation is still process-local until a SQL implementation is added;
- analytics and audit evidence can only be partial until lifecycle events move
  from the process-local in-memory trail into durable storage and projections.

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

The code now includes this transition service before any SQL implementation:

```java
interface ConversionJobStateStore {
    Optional<ConversionJob> claimForProcessing(UUID jobId, Instant now);
    void scheduleRetry(UUID jobId, String message, Instant retryAt);
    void markSucceeded(UUID jobId, String resourcePath, String message);
    void markDeadLettered(UUID jobId, String message);
    boolean retryDeadLettered(UUID jobId, String operatorId);
}
```

This keeps mutation persistence explicit. `InMemoryConversionJobRepository`
implements the interface for the current runtime, and
`RepositoryBackedConversionJobStateStore` preserves compatibility for repository
implementations that have not yet implemented transition methods directly.

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

1. Done: add `ConversionJobStateStore` with in-memory implementation and tests.
2. Done: refactor `DefaultConversionWorker` and operator retry to use explicit
   transition methods.
3. Done: add process-local append-only lifecycle events to
   `InMemoryConversionJobRepository` for every current transition, with tests
   proving event order and source-metadata omission.
4. Keep `ConversionJobRepository` read methods stable for controllers and KPI
   snapshots.
5. Add SQL schema migration scripts and a disabled SQL repository profile.
6. Add repository contract tests that run against in-memory and SQL
   implementations.
7. Add restart recovery tests for due retries and stale `PROCESSING` jobs.
8. Map process-local lifecycle events to durable metrics events and daily
   projections.
9. Turn on SQL profile only in a buyer sandbox after artifact store persistence
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

- Final attribution/legal release review remains external to this persistence
  plan.
- Production OIDC/JWT validation remains external to this persistence plan.
- Durable artifact store selection must be finalized before SQL job persistence
  can claim full production recovery.
- Implementing a SQL repository without refactoring mutable state transitions
  would create buyer-visible inconsistency risk.
