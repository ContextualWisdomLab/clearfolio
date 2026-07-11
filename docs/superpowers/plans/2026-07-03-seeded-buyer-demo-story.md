# Seeded Buyer Demo Story Plan

Date: 2026-07-03

## Objective

Give Clearfolio a deterministic local buyer-demo story for screenshots, FigJam,
and buyer-deck review without introducing a database seed path, new frontend
framework, separate library, or Git submodule.

## Product Design brief

- Keep the first screen as the actual buyer-demo work surface.
- Add a compact control to the existing upload panel instead of a new page.
- Seed the same session-history, KPI, KPI evidence, recovery evidence, and job
  detail surfaces buyers already inspect in the demo.
- Label the story as local browser-session demo evidence, not production data.

## Data Analytics brief

The fixture must cover the buyer KPI story with a minimal local dataset:

- at least one successful previewable job;
- one processing job;
- one unsupported-format state;
- one failed and dead-lettered state;
- a KPI snapshot with total jobs, success rate, and p95 preview latency;
- at least one KPI export evidence record.

## Ponytail decision

Do not add a backend seed endpoint, SQL fixture, frontend framework, submodule,
or separate library. A static JSON fixture plus the existing root-shell
JavaScript is enough for screenshot and Figma handoff work. A durable seed
mechanism should wait until the SQL repository profile exists.

## TDD notes

RED was captured with `mvn -Dtest=ViewerUiControllerTest test` before
implementation. The failing assertions covered the missing `Load demo story`
button, missing fixture URL and loader hook, and missing fixture resource.

GREEN was captured with the same targeted command after implementation.

## Acceptance

- `GET /` renders `id="load-demo-data-btn"` with `Load demo story`.
- `demo.js` fetches `/assets/viewer/demo-fixtures.json`.
- Loading the seed stores the fixture in browser session history and renders KPI
  snapshot/export panels without calling a new backend seed endpoint.
- `demo-fixtures.json` includes succeeded, processing, unsupported-format, and
  dead-lettered states.
- FigJam contains `Clearfolio Seeded Buyer Demo Story Flow`.
- Fresh AGENTS gates are recorded in
  `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/seeded-demo-story-verification.md`.
