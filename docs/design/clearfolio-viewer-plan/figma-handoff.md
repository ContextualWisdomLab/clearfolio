# Figma Handoff

Date: 2026-07-02

## Figma File

- File: <https://www.figma.com/design/UPIMvnjyP1sTXhIy1wqD02>
- Page: `Clearfolio plan`
- Root node: `2:2`
- Local screenshot: [figma-board-screenshot.png](figma-board-screenshot.png)

## What Was Created

- A self-contained editable board titled
  `Clearfolio Viewer: no-Code-Connect design plan`
- PR queue metric cards for open, blocked, dirty, and changes-requested counts
- PR theme segmentation bars
- Clearfolio local token swatches derived from `viewer.css`
- desktop state frames for loading, ready, and failed
- mobile loading frame
- tablet two-pane concept frame
- audit notes tied to the current repo constraints

## Figma Tooling Notes

- Code Connect was not used.
- `search_design_system` was called with Code Connect disabled.
- No reusable Figma component mapping was generated.
- No production code was generated from Figma.
- Material/Simple libraries were visible in the file, but searches returned no
  usable component or token matches for this pass, so the board uses local
  primitives and repo tokens.

## Follow-Up Usage

Use PR #57, `palette/viewer-ux-improvements-2659630210570478270`, as the
canonical UX source unless a later live re-check shows a better current-head
candidate. The Figma board now includes this note.

Implementation mode: rebase PR #57 or extract its minimal viewer patch after
security/Sentinel conflicts are checked. Do not blindly merge it while it is
`DIRTY`.
