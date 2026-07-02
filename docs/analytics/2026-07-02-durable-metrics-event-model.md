# Durable Metrics Event Model

Date: 2026-07-02

This document defines the durable event model needed to turn the current
tenant-filtered, runtime-only KPI snapshot into buyer-grade analytics evidence.
It is a design artifact. The current implementation still calculates KPI values
from in-memory jobs through `GET /api/v1/analytics/kpi-snapshot`, but each
authorized export can now be recorded to an optional local append-only snapshot
ledger when `clearfolio.analytics-snapshot-ledger.path` is configured.

## Goal

Create an append-only metrics event stream that proves reliability, latency,
volume, retry recovery, artifact access, and commercial KPI readiness without
storing raw customer documents or approval tokens.

The first durable implementation should stay inside the main repository. A
separate analytics library is not justified until another service or SDK
consumes the same event contract.

## Primary Decisions Supported

| Decision | Metrics needed | Buyer relevance |
| --- | --- | --- |
| Is the product reliable? | Success rate, failed jobs, dead letters | Reliability discount or premium. |
| Is the workflow fast enough? | P50/P95 time to preview | Workflow value claim. |
| Is the system operable? | Retry scheduled, retry accepted, recovery rate | Support and operations burden. |
| Is usage growing? | Monthly documents, active tenants, active users | ARR and demand proof. |
| Is preview access controlled? | Link created, artifact read, expired/revoked access | Security diligence. |
| Is margin plausible? | Bytes served, conversion duration, storage class | Cost model input. |

## Event Envelope

Every event uses this envelope:

```json
{
  "eventId": "018f...",
  "eventType": "conversion.job.submitted",
  "eventVersion": 1,
  "occurredAt": "2026-07-02T06:45:00Z",
  "tenantId": "tenant-placeholder",
  "actorId": "user-or-service-placeholder",
  "actorType": "user",
  "jobId": "0f2b...",
  "correlationId": "trace-or-request-id",
  "causationId": "prior-event-id",
  "sourceSurface": "buyer-demo",
  "payload": {}
}
```

Rules:

- `eventId` is globally unique and idempotent.
- `tenantId` is nullable only until the tenant model exists; after that it is
  required.
- `actorId` is nullable for anonymous demo events and required for production.
- `correlationId` should use the request trace id when available.
- `payload` must never contain source document bytes, approval tokens, signed
  artifact tokens, or full filenames.
- Event versions are additive. Breaking payload changes create a new version.

## Event Types

| Event type | Required payload | KPI projection |
| --- | --- | --- |
| `conversion.job.submitted` | `fileExtension`, `sizeBucket`, `contentType`, `blockedFormat` | Total jobs, volume mix. |
| `conversion.validation.rejected` | `reason`, `fileExtension`, `sizeBucket` | Unsupported explanation rate. |
| `conversion.processing.started` | `attemptCount`, `queueWaitMs` | Queue latency. |
| `conversion.retry.scheduled` | `attemptCount`, `delayMs`, `failureCategory` | Retry behavior. |
| `conversion.retry.accepted` | `operatorIdHash`, `previousAttemptCount` | Operator recovery. |
| `conversion.job.succeeded` | `durationMs`, `attemptCount`, `artifactChecksum` | Success rate, time to preview. |
| `conversion.job.failed` | `durationMs`, `attemptCount`, `failureCategory`, `deadLettered` | Failure and dead-letter rate. |
| `artifact.link.created` | `ttlSeconds`, `purpose`, `scope` | Controlled preview evidence. |
| `artifact.read` | `rangeRequested`, `servedBytes`, `statusCode` | Usage and cost inputs. |
| `analytics.snapshot.exported` | `windowStart`, `windowEnd`, `consumer` | Evidence freshness; first runtime slice is optional local KPI snapshot ledger evidence, not the full event stream. |

## Privacy and Classification

Allowed event fields:

- UUIDs for event, job, tenant, user/service subject, and correlation.
- File extension, content type, and size bucket.
- Hashes or checksums needed for dedupe and artifact binding.
- Timing, status, retry, and failure category.
- Served byte counts and HTTP status classes.

Disallowed event fields:

- Raw source document bytes.
- Converted PDF bytes.
- Approval tokens or signed artifact tokens.
- Full filenames by default.
- Free-form converter stderr/stdout unless redacted and classified.

If filename analytics are later required, store only a separately reviewed
`fileNameHash` and keep the raw filename in tenant-scoped operational metadata,
not in metrics events.

## Storage Design

Recommended first implementation: PostgreSQL append-only table.

```sql
CREATE TABLE analytics_events (
  event_id UUID PRIMARY KEY,
  event_type TEXT NOT NULL,
  event_version INTEGER NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  tenant_id UUID,
  actor_id TEXT,
  actor_type TEXT,
  job_id UUID,
  correlation_id TEXT,
  causation_id UUID,
  source_surface TEXT NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_events_type_time
  ON analytics_events (event_type, occurred_at);

CREATE INDEX idx_analytics_events_tenant_time
  ON analytics_events (tenant_id, occurred_at);

CREATE INDEX idx_analytics_events_job
  ON analytics_events (job_id, occurred_at);
```

Projection table:

```sql
CREATE TABLE conversion_kpi_daily (
  metric_date DATE NOT NULL,
  tenant_id UUID,
  total_jobs BIGINT NOT NULL,
  succeeded_jobs BIGINT NOT NULL,
  failed_jobs BIGINT NOT NULL,
  dead_lettered_jobs BIGINT NOT NULL,
  p95_time_to_preview_ms BIGINT,
  p95_queue_wait_ms BIGINT,
  retry_recovery_rate NUMERIC(8, 5),
  PRIMARY KEY (metric_date, tenant_id)
);
```

The projection can be rebuilt from the append-only events. Keep the raw event
stream as the source of truth and treat the daily table as a buyer-reporting
optimization.

## KPI Formulas

| KPI | Formula |
| --- | --- |
| Successful preview rate | `conversion.job.succeeded / conversion.job.submitted` for supported formats. |
| P95 time to preview | P95 of `conversion.job.succeeded.durationMs`. |
| P95 queue wait | P95 of `conversion.processing.started.queueWaitMs`. |
| Dead-letter rate | `deadLettered=true conversion.job.failed / conversion.job.submitted`. |
| Retry recovery rate | Retried jobs later succeeded / `conversion.retry.accepted`. |
| Artifact access rate | `artifact.read status 2xx / artifact.link.created`. |
| Monthly document volume | Count of `conversion.job.submitted` per calendar month. |
| Active tenants | Count of tenants with at least one submitted job in the window. |

## Event Emission Points

| Current code point | Future event |
| --- | --- |
| `ConversionController.submit` | `conversion.job.submitted` after accepted response id exists. |
| `DefaultDocumentValidationService.validateOrThrow` | `conversion.validation.rejected` before throwing. |
| `ConversionJob.markProcessing` | `conversion.processing.started`. |
| `DefaultConversionWorker.onFailure` | `conversion.retry.scheduled` or `conversion.job.failed`. |
| `ConversionJob.retryDeadLetteredToSubmitted` | `conversion.retry.accepted`. |
| `ConversionJob.markSucceeded` | `conversion.job.succeeded`. |
| Signed artifact link endpoint | `artifact.link.created`. |
| `ArtifactController.getPdf` | `artifact.read`. |
| `AnalyticsController.kpiSnapshot` | `analytics.snapshot.exported`; current slice records exported snapshot fields in `KpiSnapshotLedger` when a local ledger path is configured. |

## Buyer Acceptance Criteria

- A buyer can audit every KPI back to immutable event types and formulas.
- Runtime KPI endpoint can keep its current response shape while switching its
  source from tenant-filtered in-memory jobs to durable projections.
- Authorized KPI exports leave local append-only evidence when the snapshot
  ledger path is configured.
- Events are tenant-scoped before paid pilots.
- Metrics events do not carry raw customer document content or secrets.
- Failure categories are controlled values, not raw exception strings.
- Projections are rebuildable from the event table.

## Implementation Sequence

1. Keep the KPI response contract stable while recording authorized exports in
   `KpiSnapshotLedger`.
2. Define `AnalyticsEvent` and `AnalyticsEventRepository` in the existing app.
3. Add an in-memory implementation for tests and MVP parity.
4. Emit lifecycle events at the code points listed above.
5. Promote snapshot export evidence into the same append-only event contract.
6. Add PostgreSQL persistence only after tenant and deployment design are ready.
7. Add daily projection generation and evidence export.
8. Add event schema version tests and redaction tests.
