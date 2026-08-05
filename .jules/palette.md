# Palette engineering journal

## 2026-08-05 — Shared accessible async controls

### Learning

Repeated table actions need row-specific accessible names such as `View details for report.pdf`; visible labels alone are ambiguous when a screen reader lists controls out of table context.

Asynchronous controls must preserve their original child-node identities, disabled state, `aria-busy`, and `aria-label`. Backing up markup with `innerHTML` reparses untrusted-looking filenames and can destroy event listeners or element state. The project therefore uses `Array.from(button.childNodes)` and `replaceChildren(...)` through one shared, nested-safe helper.

### Applied pattern

- `createActionButton` and `createLink` use `textContent` for visible labels so markup-like filenames remain inert text.
- `setBusyState` sets visible loading text, `disabled`, `aria-busy="true"`, and a contextual loading accessible name.
- The helper reference-counts overlapping operations and returns an idempotent restore callback.
- The original state is restored only after every caller releases its busy-state claim.
- Executable Node tests cover enabled and initially disabled controls, pre-existing and empty ARIA values, nested callers, duplicate restores, contextual labels, and markup-like text.
- Maven runs those tests with 100% line, branch, and function coverage thresholds for the production DOM helper.

### Future rule

New asynchronous UI actions must reuse the shared helper rather than introducing local state-restoration code. Browser-level flows should additionally verify focus, cancellation, authorization failure, network failure, and duplicate activation prevention when those states are introduced.

## 2026-07-13 — Details loading feedback

The session history `Details` action now gives immediate visible loading feedback and prevents duplicate activation while job evidence is loading. The shared helper preserves nested DOM content and restores the exact original state after success or failure.

## 2024-05-18 — Refresh-evidence feedback

KPI evidence refresh benefits from explicit pending feedback because network latency otherwise looks like an unresponsive control. The same shared busy-state contract applies to refresh, retry, seeded-demo loading, and document submission actions.
