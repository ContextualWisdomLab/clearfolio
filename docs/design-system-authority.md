# Design-system authority

## Decision

Clearfolio keeps the shipped WebFlux/static JavaScript/CSS viewer as the production UI architecture. `src/main/resources/static/assets/viewer/viewer.css` is the runtime source of truth for shipped visual tokens and reusable viewer primitives. Storybook is development-only executable design evidence; it does not become a second production frontend or network authority.

`design/tokens/clearfolio.tokens.json` is a Design Tokens Community Group (DTCG) 2025.10 interchange projection of the accepted runtime colors. The Buyer-readiness contract fails when its hexadecimal and sRGB values drift from the CSS source. The projection does not silently overwrite runtime CSS.

## Executable buyer states

`design/storybook/viewer-states.stories.js` covers the buyer-decision states currently represented by the live Figma plan and the repository viewer boundary:

- loading and busy/disabled controls;
- ready;
- preview failure;
- document not found;
- invalid document link;
- network interruption;
- keyboard focus behavior;
- mobile loading;
- tablet-width ready state;
- reduced-motion and forced-colors alternatives.

Every error state gives a concrete next action. Loading copy explains that no action is required yet. The stories use only a fixed all-zero document reference and make no network requests; they contain no customer data, tenant authority, signed links, or credentials.

Project-level Storybook accessibility configuration sets `a11y.test` to `error` and runs WCAG A/AA tags through WCAG 2.2 AA plus axe best-practice checks. The Storybook Vitest integration executes smoke and `play` interaction tests in headless Chromium through Vitest Browser Mode and Playwright.

## Figma traceability

Fresh evidence was re-read on 2026-08-23 from Clearfolio Figma file `UPIMvnjyP1sTXhIy1wqD02`, page `0:1`, board `2:2` (`Clearfolio Viewer Product Design + Analytics Board`). The board identifies itself as a no-Code-Connect design plan and contains desktop loading/ready/failed, mobile loading, and tablet concepts. Its local swatches match protected-main runtime values: brand `#034ea2`, ink `#102032`, muted `#5a6777`, background `#f6f7f9`, panel `#ffffff`, line `#d7dde6`, danger `#b42318`, and focus `#ff595a`.

Code Connect component inventory cannot currently be treated as executable evidence. The connected Figma account reports a Full seat on a Pro team, while the Code Connect API requires a Dev or Full seat on an Organization or Enterprise plan. Until that entitlement changes, the repository records the limitation and does not fabricate mappings. Storybook-to-runtime parity remains independently executable.

## Security and operability boundary

Storybook packages are development dependencies only. The dedicated pull-request workflow checks out the exact PR head, verifies the checked-out SHA, installs strictly from the reviewed checked-in `package-lock.json` with `npm ci --ignore-scripts --no-audit --no-fund`, runs the Python design-system contract suite, installs only the Chromium browser required for the test provider, builds Storybook, and executes the browser tests. No model credential, repository write token, application secret, customer fixture, or runtime deployment authority is required.

The checked-in npm lockfile makes transitive development dependencies reviewable and reproducible between runs. A successful Storybook run is design evidence only; it does not replace Maven verification, security scans, protected-branch requirements, review-thread resolution, or qualifying independent approval.

## References

Design Tokens Community Group. (2025, October 28). *Design Tokens Format Module 2025.10*. https://www.designtokens.org/TR/2025.10/format/

Design Tokens Community Group. (2025, October 28). *Design Tokens Color Module 2025.10*. https://www.designtokens.org/TR/2025.10/color/

Storybook. (2026). *Accessibility tests*. https://storybook.js.org/docs/writing-tests/accessibility-testing

Storybook. (2026). *Testing in CI*. https://storybook.js.org/docs/writing-tests/in-ci

Storybook. (2026). *Vitest addon*. https://storybook.js.org/docs/writing-tests/integrations/vitest-addon

Storybook. (2026). *Storybook for Web Components with Vite*. https://storybook.js.org/docs/get-started/frameworks/web-components-vite

Vitest. (2026). *Browser mode*. https://vitest.dev/guide/browser/
