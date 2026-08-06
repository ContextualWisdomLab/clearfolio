# Durable artifact cleanup integration design

- **Status:** Implemented on the review branch
- **Decision date:** 2026-08-06
- **Parent security finding:** PR #268, incomplete artifact cleanup (CWE-459)
- **Foundation:** `ArtifactDeletionReceiptStore` and `RECEIPT_V1` ledger

## Objective

An authorized job deletion must never lose the information needed to remove confidential artifact bytes. The service must durably record deletion intent before metadata mutation, recover every incomplete lifecycle after restart, retry storage failures with bounded serial work, expose low-cardinality metrics, serialize same-job conversion and deletion, and never delete bytes belonging to a different immutable job lifecycle.

## Chosen approach

Create one focused `ArtifactDeletionCoordinator` that owns the deletion lifecycle and is shared by the HTTP service and a restart/scheduled recovery path. The coordinator uses the existing repository, artifact-store, and receipt-store interfaces rather than embedding file-ledger details in the controller or conversion service. A fixed-memory `ArtifactLifecycleLockRegistry` is shared with `DefaultConversionWorker` so an in-flight same-process conversion cannot recreate bytes after cleanup completion.

The alternatives were rejected as follows:

1. **Log-only retry hint:** insufficient because logs are not idempotent durable work and cannot prove recovery.
2. **Controller-local queue:** insufficient because process restart loses work and MSA adapters cannot reuse the contract.
3. **Cleanup without a conversion fence:** insufficient because an already-claimed worker could write after deletion completed.
4. **New database transaction in this slice:** stronger for a database deployment, but the repository currently has no database adapter. The receipt-first state machine supplies crash recovery now while retaining an interface boundary for a future transactional outbox adapter.

## Lifecycle and data flow

1. Resolve the job through a tenant-scoped repository query. Cross-tenant and missing jobs return `false` without artifact-store or receipt-store access.
2. Enter the fixed-stripe lock for the permanently reserved job identifier. Conversion holds the same stripe from claim through artifact write and terminal processing transition.
3. Read the current artifact before metadata mutation. A present artifact is bound by its lowercase SHA-256 digest. Absence is represented by the SHA-256 digest of the empty byte sequence; because job identifiers are permanently reserved, any later artifact under that tombstoned identifier still belongs to the same lifecycle and is safe to remove.
4. Durably create or reuse the job-bound `DELETION_REQUESTED` receipt. The audit correlation value is a random receipt correlation and contains no tenant, subject, filename, token, or storage path.
5. Tombstone metadata. The coordinator records `METADATA_TOMBSTONED` only after the repository confirms deletion or confirms that the already-authorized immutable job is absent.
6. Record `ARTIFACT_CLEANUP_PENDING` and attempt artifact deletion immediately.
7. Before deletion, compare any present artifact with the receipt digest. A mismatched non-empty digest fails closed as `artifact_checksum_mismatch`. The empty digest is the documented absence sentinel and permits deletion of a late write for the same permanently reserved job identifier.
8. A successful `ArtifactStore.deletePdf` transition records `ARTIFACT_CLEANUP_COMPLETED`. Read and delete exceptions become controlled `artifact_store_read_failed` or `artifact_store_delete_failed` codes; exception messages and paths never enter the ledger or metric tags.
9. Startup and a fixed-delay scheduler replay at most a configured number of incomplete receipts in deterministic order. `DELETION_REQUESTED`, `METADATA_TOMBSTONED`, `ARTIFACT_CLEANUP_PENDING`, and `ARTIFACT_CLEANUP_FAILED` are all resumable. Processing is serialized to prevent duplicate local transitions; durable MSA adapters must provide equivalent distributed generation fencing and single-consumer or compare-and-set semantics.

## Concurrency boundary

`ArtifactLifecycleLockRegistry` hashes job identifiers across 256 `ReentrantLock` stripes. The fixed stripe count bounds memory independently of document volume. Unrelated jobs remain concurrent unless they collide on one stripe; a collision can reduce throughput but cannot weaken lifecycle ordering. Locks are released in `finally`, including exceptional paths.

The registry is intentionally process-local. A multi-instance deployment must add an immutable object generation precondition, database compare-and-set tombstone, distributed lock with fencing token, or transactional outbox consumer contract. Documentation and buyer evidence must not describe the JVM-local fence as a cluster-wide guarantee.

## Backpressure and failure handling

`clearfolio.artifact-deletion-cleanup.max-receipts-per-run` bounds one recovery pass and defaults to 100. `clearfolio.artifact-deletion-cleanup.retry-delay-ms` defaults to 30000. Cleanup is intentionally retried without a terminal attempt limit because retention of confidential bytes is not an acceptable permanent failure mode; operators alert on the pending gauge and failure counter.

A repository or receipt-store exception propagates and leaves earlier durable evidence unchanged. Artifact-store read or delete exceptions are converted only after a receipt is cleanup-eligible, preserving controlled retry evidence. Completed receipts remain immutable and are not replayed.

## Metrics and observability

Spring Boot Actuator supplies a `MeterRegistry`. The integration registers:

- `clearfolio.artifact.deletion.attempts` counter with bounded `outcome=completed|failed`;
- `clearfolio.artifact.deletion.pending` gauge backed by `ArtifactDeletionReceiptStore.pendingCount()`.

These meter names can be exported through an OpenTelemetry-compatible Micrometer registry without changing the coordinator. No raw identifier, exception text, filename, tenant, subject, token, checksum, or path is used as a tag.

## Compatibility boundary

Standalone constructors use an in-memory receipt ledger and simple meter registry. Spring runtime uses the durable file ledger by default. A PostgreSQL/outbox implementation may replace `ArtifactDeletionReceiptStore`, and a remote object-store implementation may replace `ArtifactStore`, while preserving receipt identity, permanent job-id reservation, monotonic transitions, exact/sentinel digest semantics, idempotency, conversion-versus-deletion ordering, and fail-closed conflicts.

Future database object names must contain at least two descriptive words and use `snake_case`, such as `artifact_deletion_receipt`, `artifact_cleanup_task`, and `conversion_job_tombstone`.

## Verification contract

Tests must prove successful deletion, cross-tenant concealment, read failure before metadata mutation, delete failure persistence, controlled metrics, deterministic retry, restart replay, already-tombstoned recovery, digest mismatch rejection, late-artifact removal for the absence sentinel, deterministic in-flight conversion fencing, fixed-memory lock release, bounded recovery batches, invalid configuration rejection, and no secret-bearing metric tags. Production statement and branch coverage remain 100%, public Javadocs remain warning-free, and the integrated exact head must pass CI, Security Scan, SAST, fuzz, CodeRabbit, OpenCode/Noema, Strix, and independent approval before merge.

## References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110; STD 97). Internet Engineering Task Force.

National Institute of Standards and Technology. (2024). *Protecting controlled unclassified information in nonfederal systems and organizations* (NIST Special Publication 800-171, Revision 3). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-171r3

OWASP Foundation. (2023). *API1:2023 broken object level authorization*. In *OWASP API Security Top 10—2023*.
