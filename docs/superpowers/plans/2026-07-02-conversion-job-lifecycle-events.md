# Conversion Job Lifecycle Events Plan

Date: 2026-07-02

## Objective

Add the smallest useful append-only lifecycle event trail for the current
in-memory conversion job repository so buyer KPI and persistence diligence can
trace job transitions before the SQL repository profile exists.

## Constraints

- Use TDD: add failing repository tests before production code.
- Do not add a new dependency, library split, or Git submodule.
- Do not introduce a new event-store interface until a second implementation
  exists.
- Do not store source document bytes, source filenames, content hashes, signed
  artifact tokens, artifact paths, or raw converter error text in lifecycle
  events.
- Keep SQL persistence, restart recovery, and KPI projections as explicit
  follow-up work.

## Steps

1. Add repository tests proving:
   - `findOrStoreByContentHash` records `conversion.job.submitted`;
   - duplicate uploads record `conversion.job.dedupe_hit`;
   - state-store transitions record processing, retry, success, failure, and
     operator retry acceptance in order;
   - lifecycle event text omits raw source metadata.
2. Implement `ConversionJobLifecycleEvent` as a versioned value object.
3. Append lifecycle events inside `InMemoryConversionJobRepository`.
4. Expose read-only job and tenant event snapshot methods for tests and future
   projection work.
5. Update diligence, analytics, persistence, README, and FigJam handoff docs.
6. Run targeted repository tests, then the full AGENTS gate stack.

## Acceptance

- Repository lifecycle event tests pass.
- Existing state-store behavior remains unchanged.
- New lifecycle events are process-local and documented as partial buyer
  evidence, not durable persistence.
- Mandatory Maven, coverage, JavaDoc, Markdown, security, and license gates
  remain green.
