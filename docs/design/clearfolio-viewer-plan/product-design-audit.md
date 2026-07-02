# Clearfolio Viewer Product Design Audit And Brief

Date: 2026-07-02

## Design Brief

Design a conservative Clearfolio document viewer direction for the existing
`/viewer/{docId}` shell. The design should clarify loading, ready, failed,
missing, invalid, and network-error states without replacing the Java WebFlux
HTML shell or adding a frontend framework.

Visual source:

- `src/main/resources/static/assets/viewer/viewer.css`
- `src/main/resources/static/assets/viewer/viewer.js`
- `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`
- `docs/ui/ux-ver3-checklist.md`

Interactivity target:

- Static Figma state board now.
- Full browser behavior only after a canonical UX branch is selected.

## Current UI Observations

### Strengths

- The shell has a skip link, fixed header, status region, alert region, and
  `aria-busy` on the preview container.
- `viewer.js` validates UUID shape before polling and blocks unsafe preview
  URLs before creating artifact links or PDF.js iframes.
- The page already covers loading, failed, not found, invalid docId, and
  network-error messages through existing state paths.
- The CSS has a small token set: brand, ink, muted, background, panel, line,
  danger, focus, radius, and shadow.

### Risks

- The current visual system uses 12px radii across panels and buttons while
  the broader design guidance prefers tighter operational UI. Keep 12px only
  if existing viewer consistency matters more than broader product density.
- The `Open JSON bootstrap` action is useful for operators and debugging, but
  it may read as end-user functionality. Hide or relabel it if the auth model
  does not guarantee it is appropriate for viewer users.
- Loading copy currently changes the primary button to `Refreshing...`, but no
  spinner or distinct progress state exists in main. This overlaps with many
  open Palette PRs and should be consolidated rather than reimplemented again.
- The tablet two-pane layout is only justified if the product later exposes
  thumbnails, status history, page navigation, or document metadata. Do not add
  a two-pane layout purely as decoration.

## State Coverage

| State | Current source | Design treatment |
| --- | --- | --- |
| Loading | `setLoading()` | Blue status panel, disabled primary action |
| Processing | `poll()` status branch | Blue status panel, retry interval text |
| Ready | `SUCCEEDED` branch | Inline PDF.js frame plus artifact link |
| Failed | `showError()` | Red alert panel plus refresh action |
| Not found | 404 branch and initial state | Red alert panel with plain explanation |
| Invalid docId | `isUuidLike()` branch | Red alert panel before polling |
| Network error | catch block | Red alert panel with retry prompt |

## Figma Direction

The Figma board uses repo-local tokens and shows:

- PR queue summary cards
- theme segmentation bars
- local Clearfolio color tokens
- desktop loading, ready, and failed states
- mobile loading state
- tablet two-pane concept
- audit notes for follow-up implementation

Code Connect is intentionally not used. The file is a visual decision artifact,
not a source-generated component map.

## Recommended UI Scope

Canonical UX source: PR #57,
`palette/viewer-ux-improvements-2659630210570478270`.

Reason: PR #57 is the approved UX candidate with current successful checks and
the broadest useful viewer surface: refresh disabling, secure new-tab JSON
bootstrap behavior, preview help cleanup, and a controller assertion. It is
still `DIRTY`, so adoption should be by rebase or minimal patch extraction, not
blind merge.

If the team proceeds to code after queue consolidation, implement only:

1. a clearer disabled/loading affordance for the refresh action,
2. end-user/operator distinction for `Open JSON bootstrap`, and
3. small copy and focus checks for failed, not-found, invalid, and network
   states.

Skip for now:

- new frontend framework,
- full design-system migration,
- custom component library,
- tablet two-pane implementation without thumbnails or metadata,
- Code Connect mappings.
