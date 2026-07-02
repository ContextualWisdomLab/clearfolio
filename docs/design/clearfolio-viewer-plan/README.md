# Clearfolio Viewer Design And Analytics Plan

Date: 2026-07-02

This folder captures the approved no-Code-Connect plan for
`ContextualWisdomLab/clearfolio`.

## Current Decision

Do not create another speculative viewer UX implementation PR yet. The live
queue already contains many overlapping viewer UX, security, and performance
branches. First consolidate the decision evidence, align the Figma direction,
then implement only the smallest viewer change that survives the existing
merge gates.

## Live Baseline

- Repository: <https://github.com/ContextualWisdomLab/clearfolio>
- Default branch: `main`
- Baseline commit used locally: `a1b7e8f9759910e7b9d28c837899808470c2ae02`
- Product surface: `/viewer/{docId}` HTML shell with static
  `viewer.css` and `viewer.js`
- Runtime: Java 21, Spring Boot WebFlux, Maven
- Product description: integrated document viewer platform
- Required gates: compile, tests, 100% JaCoCo line/branch coverage, JavaDoc,
  markdown lint, and security evidence

## Artifacts

- [PR queue analytics](pr-queue-analytics.md)
- [Product Design audit and brief](product-design-audit.md)
- [Figma handoff](figma-handoff.md)
- [Figma board screenshot](figma-board-screenshot.png)
- [Superpowers implementation plan](../../superpowers/plans/2026-07-02-clearfolio-viewer-design-analytics.md)

## Recommended Next Implementation Path

1. Treat the Figma file as the visual decision board, not a Code Connect source.
2. Treat PR #57, `palette/viewer-ux-improvements-2659630210570478270`, as the
   canonical UX source unless a newer live re-check supersedes it.
3. If code work is still needed after consolidation, touch only:
   - `src/main/resources/static/assets/viewer/viewer.css`
   - `src/main/resources/static/assets/viewer/viewer.js`
   - `src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`
   - matching tests under `src/test/java/com/clearfolio/viewer/controller/`
4. Keep all implementation inside the current WebFlux HTML shell. Do not add a
   frontend framework, build pipeline, or new runtime dependency for this pass.
