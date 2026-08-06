# Artifact deletion cleanup operations

## Purpose

This runbook covers the durable cleanup worker introduced for issue #263. It is
for operators of the standalone Spring service and for teams implementing a
compatible database, queue, or object-store adapter.

The authenticated administrator DELETE request may return after metadata has
been tombstoned while physical byte deletion remains retryable. Until the API
status slice is integrated, the authoritative physical-cleanup state is the
`ArtifactDeletionReceiptStore`, not the HTTP response alone.

## Safe operational signals

`ArtifactDeletionMetrics` exposes only aggregate, dimension-free values:

- `completedAttempts()` — cleanup attempts that reached a durable completed
  receipt;
- `failedAttempts()` — controlled failures retained for retry;
- `pendingReceipts()` — all nonterminal durable receipts;
- `recoveryBatchRuns()` — measured bounded recovery invocations;
- `recoveryBatchTotalDuration()` — cumulative elapsed recovery time;
- `recoveryBatchMaximumDuration()` — longest measured recovery invocation.

Export these values through the deployment's existing OpenTelemetry, JMX, or
metrics adapter. Do not add tenant identifiers, job identifiers, checksums,
filenames, exception messages, tokens, subjects, document content, or storage
paths as labels or log fields.

Average recovery duration is derived as
`recoveryBatchTotalDuration / recoveryBatchRuns` when the run count is nonzero.
The maximum is retained separately so a low average cannot conceal a stalled
storage call.

## Alert conditions

Alert when any of the following evidence persists across at least two scheduled
retry intervals:

1. `pendingReceipts()` is greater than zero and does not decrease.
2. `failedAttempts()` increases while `completedAttempts()` does not.
3. `recoveryBatchMaximumDuration()` approaches the scheduler interval or the
   artifact-store client timeout.
4. The receipt ledger cannot be loaded, contains an unterminated final record,
   or rejects an illegal transition.
5. Disk capacity for the ledger or artifact root approaches the deployment's
   reserved minimum.

A transient failed-attempt increment followed by completed growth and a falling
pending count is expected recovery evidence, not data loss.

## Investigation sequence

1. Preserve the current append-only receipt ledger as restricted incident
   evidence. Do not edit or truncate it.
2. Confirm the service exact build provenance, receipt format version, artifact
   store configuration, and scheduler settings.
3. Determine whether pending receipts are in `DELETION_REQUESTED`,
   `METADATA_TOMBSTONED`, `ARTIFACT_CLEANUP_PENDING`, or
   `ARTIFACT_CLEANUP_FAILED`.
4. Validate storage reachability, credentials, filesystem permissions, quota,
   object-version preconditions, and timeout behavior without printing a raw
   document path or identifier.
5. Correct the infrastructure cause and allow startup or scheduled replay to
   resume the receipt. Do not fabricate a completed transition.
6. Confirm that pending count falls, completed count increases, and no new
   controlled failure is recorded.
7. Retain only privacy-safe aggregate evidence in a buyer-shareable report.
   Keep the raw ledger, infrastructure logs, and document identifiers in the
   restricted operational boundary.

## Restart and rollback

Startup replay processes a bounded deterministic batch. A malformed existing
ledger fails closed; replacing it with an empty file is not a valid recovery.
Restore the exact last known durable ledger from protected operational backup or
repair the storage fault that prevented reading it.

Application rollback is safe only to a build that understands every receipt
format already present. A build that does not recognize `RECEIPT_V1`, the current
state machine, or the generation fence must not start against the ledger. Preserve
artifacts and receipts until the compatible build has resumed all pending work.

## Multi-instance requirements

The reference lock registry fences one JVM. A multi-instance deployment must add
one of the following at the adapter boundary:

- a transaction that commits conversion-job tombstone, deletion receipt, and
  cleanup outbox task together;
- an immutable object generation or version precondition;
- compare-and-set receipt transitions plus a fenced single consumer;
- an equivalent distributed generation fence that cannot be extended by an
  expired lock holder.

A plain distributed mutex without a fencing token is not sufficient evidence
that a stale worker cannot publish bytes after deletion.

## Acceptance evidence

Before release, verify the exact integrated head with full Maven verification,
zero skipped tests, zero missed production lines and branches, warning-free
public Javadocs, exact-head and synthetic-merge CI, security scanning, SAST,
fuzzing, Strix, CodeRabbit, OpenCode/Noema, and counted independent approval.
The receipt ledger and artifact storage contents are restricted evidence and
must not be copied into public CI artifacts or acquisition data rooms.
