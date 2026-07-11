# Conversion Recovery Sweep Plan

Date: 2026-07-03

## Objective

Add the smallest production-shaped recovery contract for conversion jobs so the
worker can re-enqueue due submitted jobs and stale processing leases from the
available repository state before the SQL repository profile exists.

## Constraints

- Use TDD: add failing repository, properties, and worker tests before
  production code.
- Do not add a database dependency, library split, Git submodule, or queue
  framework in this slice.
- Keep the current `ConversionJobRepository` and `ConversionJobStateStore`
  boundaries stable for the eventual SQL implementation.
- Do not claim durable process-restart recovery while the default repository is
  still in memory.

## Steps

1. Add repository tests for `findRecoverableJobs` to prove:
   - due `SUBMITTED` jobs are selected;
   - future retries are skipped;
   - stale retryable `PROCESSING` jobs are selected;
   - fresh, missing-start, exhausted, succeeded, and failed jobs are skipped.
2. Add configuration tests proving `processingLeaseTimeoutMs` clamps to at
   least one millisecond.
3. Add worker tests proving:
   - recovery re-enqueues due submitted and stale processing jobs;
   - stale processing jobs go through retry scheduling before re-enqueue;
   - startup recovery uses the configured lease path.
4. Implement the repository default query, configuration property, and
   application-start worker recovery sweep.
5. Update persistence, architecture, diligence, QA evidence, and FigJam handoff
   documentation.
6. Run the full AGENTS gate stack before PR publication.

## Acceptance

- Targeted TDD tests pass after the implementation.
- Full Maven tests retain 100 percent JaCoCo line and branch coverage.
- The behavior is documented as process-local recovery, not durable SQL
  persistence.
- Mandatory compile, JavaDoc, Markdown, SAST, and license-policy gates remain
  green.
