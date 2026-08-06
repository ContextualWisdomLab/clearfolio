# Durable artifact cleanup Slice C implementation plan

Date: 2026-08-06  
Owner issue: #263  
Parent slice: #277 receipt ledger

## Goal

Integrate the durable receipt foundation with artifact cleanup without changing
conversion semantics, adding a dependency, or claiming the later truthful HTTP
and accessible UI slice.

## Ordered implementation

1. Add tests for repeat DELETE idempotency, cross-tenant receipt concealment,
   in-flight publication ordering, write-after-receipt rejection, restart replay,
   bounded recovery, aggregate counts, and service delegation.
2. Add a fixed-memory per-job lifecycle lock registry.
3. Add an artifact-store decorator that shares the lock and rejects writes after
   durable deletion intent.
4. Add a receipt-first cleanup coordinator with exact digest validation,
   controlled failure codes, startup replay, and scheduled bounded retry.
5. Add a primary document-service decorator that intercepts deletion only.
6. Enable Spring scheduling and configure retry delay and batch size.
7. Update the lifecycle ADR and changelog without changing dependencies, release,
   deployment, or workflow policy.
8. Run exact-head Maven verify, zero-skip report validation, JaCoCo zero-missed
   line/branch gates, warning-free public Javadocs, CI, security, SAST, fuzzing,
   and independent review before integration.

## Scope exclusions

- no new Maven dependency or management HTTP endpoint;
- no controller response change;
- no deletion status API or viewer UI;
- no signed-link revocation;
- no database/outbox adapter;
- no release, version, deployment, or workflow change.

## Rollback

Remove the primary deletion decorator, lifecycle-fenced artifact-store wrapper,
coordinator, scheduling annotation, and cleanup configuration. The receipt-ledger
parent remains intact and the previous service and artifact delegates resume
without data-format rollback.
