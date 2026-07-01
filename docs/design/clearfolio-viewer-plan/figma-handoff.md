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

Use the file to decide which existing Palette UX PR should become canonical.
After that decision, update the Figma board with the chosen PR number and only
then implement the smallest code patch needed in the existing viewer files.
