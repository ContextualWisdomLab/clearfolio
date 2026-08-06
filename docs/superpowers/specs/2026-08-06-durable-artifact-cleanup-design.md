# Durable artifact cleanup integration design

- **Status:** Accepted for clean Slice C implementation
- **Decision date:** 2026-08-06
- **Parent security finding:** PR #268, incomplete artifact cleanup (CWE-459)
- **Foundation:** `ArtifactDeletionReceiptStore` and `RECEIPT_V1` ledger from PR #277

## Objective

An authorized job deletion must never lose the information needed to remove
confidential artifact bytes. The service must durably record deletion intent
before metadata mutation, recover incomplete work after restart, retry storage
failures with bounded serial work, reject writes after deletion intent, expose
privacy-safe aggregate evidence, and never delete bytes belonging to another
immutable lifecycle.

## Chosen structure

The slice adds four focused adapters:

1. `ArtifactDeletionCoordinator` owns receipt-first tombstone and cleanup state.
2. `DurableDocumentDeletionService` is the primary service decorator and
   intercepts deletion only.
3. `ArtifactLifecycleLockRegistry` supplies bounded per-job serialization.
4. `LifecycleFencedArtifactStore` serializes artifact I/O with deletion and
   rejects publication after a receipt exists.

The existing conversion service, repository, artifact-store delegates, and
receipt ledger remain independently usable. No dependency or management HTTP
surface is added.

Rejected alternatives:

- **Log-only retry hint:** not durable or idempotent.
- **Controller-local queue:** lost on restart and not reusable by MSA adapters.
- **Direct modification of conversion service:** couples cleanup to unrelated
  submission/retry behavior and weakens modular replacement.
- **Actuator/Micrometer starter:** expands dependencies, management surface, SBOM,
  attribution, and license evidence solely to count three aggregate values.
- **New database transaction in this slice:** the repository has no database
  adapter yet; the receipt-first reference path remains replaceable by a later
  transactional outbox.

## Lifecycle flow

1. Resolve the job through a tenant-scoped query.
2. Under the per-job lifecycle lock, read the current artifact and bind it to
   lowercase SHA-256 or the documented absence sentinel.
3. Durably create or reuse `DELETION_REQUESTED`.
4. Tombstone metadata without releasing the job identifier.
5. Record `METADATA_TOMBSTONED` and `ARTIFACT_CLEANUP_PENDING`.
6. Re-read under the same lifecycle fence. A non-empty digest must match.
7. Delete exact bytes or record a controlled read/delete/mismatch failure.
8. Startup and fixed-delay recovery replay a bounded deterministic batch.
9. A later same-tenant DELETE resumes or observes the receipt; another tenant
   receives the same concealed not-found result.

## Write fence

All configured artifact operations pass through `LifecycleFencedArtifactStore`.
If an artifact publication holds the job lock first, deletion waits, snapshots
that generation, and removes it. If deletion creates a receipt first, every
later `putPdf` fails closed. This prevents a conversion that began before
metadata tombstoning from recreating bytes after cleanup completion.

The lock is process-local and fixed-memory. Multi-instance adapters must use an
object generation precondition, durable lease, distributed lock, or
transactional outbox consumer fence.

## Backpressure and recovery

`clearfolio.artifact-deletion-cleanup.max-receipts-per-run` defaults to `100`.
`clearfolio.artifact-deletion-cleanup.retry-delay-ms` defaults to `30000`.
One receipt failure is isolated from later receipts in the selected batch.
Cleanup has no terminal retry count because silently retaining confidential
bytes is not an acceptable terminal state.

## Aggregate evidence

`ArtifactDeletionMetrics` uses JDK `LongAdder` counters and the receipt store's
current pending count. It exposes only completed, failed, and pending aggregate
values. It stores no labels or identifiers and introduces no dependency. Host
applications may bridge these values to their existing observability plane.

## Verification contract

Tests must prove:

- tenant concealment and missing-context failure;
- receipt before metadata mutation;
- exact digest and absence-sentinel behavior;
- read/delete/mismatch failure persistence and retry;
- restart and scheduled recovery;
- bounded batch isolation;
- repeated same-tenant DELETE idempotency;
- cross-tenant receipt concealment;
- write-after-receipt rejection;
- in-flight publication/deletion serialization;
- delegate-mode persistence for filesystem and volatility for in-memory storage;
- decorator delegation for every non-deletion method;
- aggregate evidence without high-cardinality input;
- 100% production line and branch coverage and warning-free public Javadocs.

Exact-head CI, Security Scan, SAST, fuzzing, CodeRabbit, OpenCode/Noema, Strix,
zero unresolved threads, and independent protected-branch approval remain merge
gates.

## References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110;
STD 97). Internet Engineering Task Force.

National Institute of Standards and Technology. (2024). *Protecting controlled
unclassified information in nonfederal systems and organizations* (NIST Special
Publication 800-171, Revision 3). U.S. Department of Commerce.
https://doi.org/10.6028/NIST.SP.800-171r3

OWASP Foundation. (2023). *API1:2023 broken object level authorization*. In
*OWASP API Security Top 10—2023*.
